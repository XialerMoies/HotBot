import { ref } from "vue";
import { api } from "../services/api.js";

// Keep previous data on failure and ignore obsolete responses.
export function useResource(fetcher, initialValue) {
  const data = ref(initialValue);
  const loading = ref(false);
  const error = ref("");
  const updatedAt = ref(null);
  let generation = 0;
  async function load(...args) {
    const current = ++generation;
    loading.value = true;
    error.value = "";
    try {
      const result = await fetcher(...args);
      if (current === generation) {
        data.value = result;
        updatedAt.value = new Date().toISOString();
      }
      return result;
    } catch (err) {
      if (current === generation) error.value = api.message(err);
    } finally {
      if (current === generation) loading.value = false;
    }
  }
  function reset(value = initialValue) {
    generation++;
    data.value = value;
    loading.value = false;
    error.value = "";
  }
  return { data, loading, error, updatedAt, load, reset };
}
