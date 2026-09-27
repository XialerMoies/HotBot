"""Bounded public-page extraction. No browser/login/paywall bypass and no whole-page fallback."""
from __future__ import annotations

from html.parser import HTMLParser
import ipaddress
import socket
import time
import threading
from urllib.parse import urlsplit, urljoin
import urllib3
import certifi

MAX_BYTES = 2_000_000
FETCH_BUDGET_SECONDS = 8


def deadline_connection(base, deadline, timers):
    class BoundedConnection(base):
        def connect(self):
            super().connect()
            connected_socket = self.sock
            def abort():
                try:
                    connected_socket.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                abort()
                raise TimeoutError('Article fetch deadline')
            timer = threading.Timer(remaining, abort)
            timer.daemon = True
            timers.append(timer)
            timer.start()
    return BoundedConnection


def validate_public_url(url: str) -> list[str]:
    parsed = urlsplit(url)
    if parsed.scheme not in {'http', 'https'} or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('Public HTTP(S) URL required')
    if parsed.port not in {None, 80, 443}:
        raise ValueError('Unsupported port')
    try:
        literal = ipaddress.ip_address(parsed.hostname)
    except ValueError:
        literal = None
    if literal is not None and not literal.is_global:
        raise ValueError('Non-public address')
    addresses = [entry[4][0] for entry in socket.getaddrinfo(parsed.hostname, parsed.port or (443 if parsed.scheme == 'https' else 80), type=socket.SOCK_STREAM)]
    if not addresses or any(not ipaddress.ip_address(address).is_global for address in addresses):
        raise ValueError('Non-public address')
    return list(dict.fromkeys(addresses))


class ArticleParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.parts = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        classes = (attrs.get('class', '') + ' ' + attrs.get('id', '')).lower()
        excluded = tag in {'script', 'style', 'nav', 'footer', 'header', 'aside', 'form', 'button', 'noscript'} or any(
            x in classes for x in ['cookie', 'advert', 'newsletter', 'related-post', 'comment-list', 'paywall'])
        region = tag == 'article' or attrs.get('itemprop') == 'articleBody' or any(
            x in classes.split() for x in ['article-body', 'article-content', 'post-content', 'entry-content', 'markdown-body', 'tk-markdown', 'markdown'])
        previous = self.stack[-1] if self.stack else ('', False, False)
        active, blocked = region or previous[1], excluded or previous[2]
        if tag not in {'br', 'img', 'meta', 'link', 'input', 'hr', 'source', 'wbr'}:
            self.stack.append((tag, active, blocked))
        if tag in {'p', 'div', 'li', 'h1', 'h2', 'h3', 'br'} and active and not blocked:
            self.parts.append('\n')

    def handle_endtag(self, tag):
        for index in range(len(self.stack) - 1, -1, -1):
            if self.stack[index][0] == tag:
                if tag in {'p', 'div', 'li', 'h1', 'h2', 'h3'} and self.stack[index][1] and not self.stack[index][2]:
                    self.parts.append('\n')
                del self.stack[index:]
                break

    def handle_data(self, data):
        if self.stack and self.stack[-1][1] and not self.stack[-1][2]:
            self.parts.append(data)


def extract_article(html: str) -> str:
    parser = ArticleParser()
    parser.feed(html)
    paragraphs = [' '.join(p.split()) for p in ''.join(parser.parts).splitlines()]
    body = '\n\n'.join(p for p in paragraphs if p)
    return body[:60_000] if len(body) >= 180 else ''


def fetch_article(url: str) -> dict:
    current = url
    try:
        started = time.monotonic()
        deadline = started + FETCH_BUDGET_SECONDS
        for _ in range(4):
            addresses = validate_public_url(current)
            parsed = urlsplit(current)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return {'body': '', 'status': 'SIZE_OR_TIME_LIMIT', 'url': current}
            # Connect to the validated IP. TLS SNI and certificate validation still use the original hostname.
            options = {'timeout': urllib3.Timeout(connect=min(3,remaining), read=min(5,remaining)), 'retries': False}
            if parsed.scheme == 'https':
                pool = urllib3.HTTPSConnectionPool(addresses[0], port=443, server_hostname=parsed.hostname,
                    assert_hostname=parsed.hostname, cert_reqs='CERT_REQUIRED', ca_certs=certifi.where(), **options)
            else:
                pool = urllib3.HTTPConnectionPool(addresses[0], port=80, **options)
            path = parsed.path or '/'
            if parsed.query:
                path += '?' + parsed.query
            timers = []
            pool.ConnectionCls = deadline_connection(pool.ConnectionCls, deadline, timers)
            try:
                response = pool.urlopen('GET', path, redirect=False, preload_content=False,
                    headers={'Host': parsed.netloc, 'User-Agent': 'HotBot/1.0 (public article evidence reader)', 'Accept': 'text/html'})
                try:
                    if response.status in {301, 302, 303, 307, 308}:
                        current = urljoin(current, response.headers.get('Location', ''))
                        continue
                    if response.status != 200:
                        return {'body': '', 'status': 'FETCH_FAILED', 'url': current}
                    content_type = response.headers.get('Content-Type', '')
                    if 'text/html' not in content_type.lower():
                        return {'body': '', 'status': 'UNSUPPORTED_CONTENT', 'url': current}
                    chunks, size = [], 0
                    for chunk in response.stream(16384, decode_content=True):
                        size += len(chunk)
                        if size > MAX_BYTES or time.monotonic() >= deadline:
                            return {'body': '', 'status': 'SIZE_OR_TIME_LIMIT', 'url': current}
                        chunks.append(chunk)
                    import re
                    charset = re.search(r'charset=["\']?([\w-]+)', content_type, re.I)
                    encoding = charset.group(1) if charset else 'utf-8'
                    body = extract_article(b''.join(chunks).decode(encoding, errors='replace'))
                    return {'body': body, 'status': 'BODY' if body else 'NO_ARTICLE_BODY', 'url': current}
                finally:
                    response.close()
            finally:
                for timer in timers:
                    timer.cancel()
                pool.close()
        return {'body': '', 'status': 'REDIRECT_LIMIT', 'url': current}
    except ValueError:
        return {'body': '', 'status': 'URL_REJECTED', 'url': url}
    except Exception:
        return {'body': '', 'status': 'FETCH_FAILED', 'url': url}
