import unittest
from unittest.mock import patch
from services.article_content import extract_article, validate_public_url, fetch_article

class ArticleContentTest(unittest.TestCase):
    def test_deadline_interrupts_slow_headers_and_trickling_body(self):
        import threading,time
        from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
        from services import article_content
        original_pool=article_content.urllib3.HTTPConnectionPool
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def do_GET(self):
                try:
                    if self.path=='/headers':time.sleep(1)
                    self.send_response(200);self.send_header('Content-Type','text/html');self.send_header('Content-Length','100');self.end_headers()
                    for _ in range(100):self.wfile.write(b'a');self.wfile.flush();time.sleep(.05)
                except (BrokenPipeError,ConnectionError,OSError):pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            for path in ['/headers','/body']:
                with patch.object(article_content,'FETCH_BUDGET_SECONDS',.3,create=True),patch.object(article_content,'validate_public_url',return_value=['127.0.0.1']),patch.object(article_content.urllib3,'HTTPConnectionPool',side_effect=lambda host,**kw:original_pool(host,server.server_port,timeout=kw['timeout'],retries=False)):
                    started=time.monotonic();result=fetch_article('http://public.org'+path)
                    self.assertLess(time.monotonic()-started,.8)
                    self.assertNotEqual('BODY',result['status'])
        finally:server.shutdown();server.server_close()

    def test_official_blog_markdown_regions_exclude_surrounding_navigation(self):
        for region in ['tk-markdown', 'markdown']:
            paragraph='The compiler supports portable vector operations. '*8
            html=f'<main><div>All posts navigation</div><div class="{region}"><p>{paragraph}</p></div><div class="Footer">Newsletter</div></main>'
            self.assertEqual(paragraph.strip(),extract_article(html))

    def test_extracts_editorial_paragraphs_not_scripts_nav_or_footer(self):
        html='<html><nav>登录广告</nav><article><h1>标题</h1><p>'+('This is a real technology report. '*8)+'</p><script>secret script</script><p>Delivery starts next month.</p></article><footer>Subscribe now</footer></html>'
        body=extract_article(html)
        self.assertIn('Delivery starts next month.',body)
        for excluded in ['secret script','登录广告','Subscribe now']: self.assertNotIn(excluded,body)

    def test_missing_article_region_is_not_promoted_to_body(self):
        self.assertEqual('',extract_article('<html><body><p>Please enable JavaScript and login</p></body></html>'))

    def test_private_addresses_credentials_and_non_http_are_rejected(self):
        for url in ['http://127.0.0.1/x','http://[::1]/','http://169.254.169.254/','file:///etc/passwd','https://a:b@public.org/']:
            with self.subTest(url=url):
                with self.assertRaises(ValueError):validate_public_url(url)

    @patch('services.article_content.socket.getaddrinfo',return_value=[(2,1,6,'',('10.0.0.1',443))])
    def test_hostname_resolving_private_is_rejected(self,_):
        with self.assertRaises(ValueError):validate_public_url('https://public.org/article')

    @patch('services.article_content.socket.getaddrinfo',return_value=[(2,1,6,'',('93.184.216.34',443))])
    @patch('services.article_content.urllib3.HTTPSConnectionPool')
    def test_connection_is_pinned_to_validated_address(self,pool,dns):
        from unittest.mock import MagicMock
        response=MagicMock();response.status=200;response.headers={'Content-Type':'text/html'}
        response.stream.return_value=iter([('<article><p>'+'A real technology report. '*12+'</p></article>').encode()])
        pool.return_value.urlopen.return_value=response
        result=fetch_article('https://public.org/article')
        self.assertEqual('BODY',result['status'])
        self.assertEqual('93.184.216.34',pool.call_args.args[0])
        self.assertEqual('public.org',pool.call_args.kwargs['server_hostname'])
        self.assertEqual('public.org',pool.call_args.kwargs['assert_hostname'])
        dns.assert_called_once()

    @patch('services.article_content.socket.getaddrinfo',return_value=[(2,1,6,'',('93.184.216.34',443))])
    @patch('services.article_content.urllib3.HTTPSConnectionPool')
    def test_redirect_to_private_address_is_rejected_before_connection(self,pool,dns):
        from unittest.mock import MagicMock
        response=MagicMock();response.status=302;response.headers={'Location':'http://127.0.0.1/admin'}
        pool.return_value.urlopen.return_value=response
        result=fetch_article('https://public.org/article')
        self.assertEqual('URL_REJECTED',result['status'])
        self.assertEqual(1,pool.call_count)

    @patch('services.article_content.validate_public_url',side_effect=TimeoutError())
    def test_failure_is_explicit_not_a_fabricated_body(self,_):
        result=fetch_article('https://public.org/article')
        self.assertEqual('',result['body'])
        self.assertEqual('FETCH_FAILED',result['status'])
