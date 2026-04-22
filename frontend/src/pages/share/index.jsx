import React, { useCallback, useEffect, useMemo, useState } from "react";
import debounce from "lodash/debounce";
import { useLocation, useNavigate } from "react-router";
import Page from "../../components/Page";
import { notify } from "../../components/Notification";
import { useCategoriesList } from "../../context/categoriesList";
import { BUTTON_BASE_CLASS } from "../../helpers/baseDesign";
import { postJSON } from "../../helpers/fetch";
import TagInput from "../addBookmark/components/TagInput";

const DEFAULT_CATEGORY_COLOR = "#06b6d4";

function extractUrl(text = "") {
  return text.match(/https?:\/\/[^\s<>"']+/i)?.[0] || "";
}

function getInitialShareData(search) {
  const params = new URLSearchParams(search);
  const text = params.get("text") || "";
  const url = params.get("url") || extract(text);

  return {
    url,
    title: params.get("title") || "",
    desc: text.replace(url, "").trim(),
    text,
  };
}

export default function SharePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const initialShareData = useMemo(
    () => getInitialShareData(location.search),
    [location.search]
  );
  const { categories, fetchCategories } = useCategoriesList();
  const [url, setUrl] = useState(initialShareData.url);
  const [formData, setFormData] = useState({
    title: initialShareData.title,
    desc: initialShareData.desc,
    icon: "",
    categoryId: 0,
    newCategoryName: "",
    newCategoryColor: DEFAULT_CATEGORY_COLOR,
    tags: [],
  });
  const [isLoadingInfo, setIsLoadingInfo] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [complete, setComplete] = useState(null);
  const [error, setError] = useState();

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const debouncedFetchInfo = useCallback(debounce(fetchUrlInfo, 500), [url]);

  useEffect(() => {
    fetchCategories();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (url) debouncedFetchInfo(url);
    return debouncedFetchInfo.cancel;
  }, [debouncedFetchInfo, url]);

  useEffect(() => {
    if (error)
      notify("Share failed", error?.message || "Cannot save link", "error");
  }, [error]);

  async function fetchUrlInfo(inputUrl = url) {
    if (!inputUrl.startsWith("http")) return;

    setIsLoadingInfo(true);
    setError(undefined);
    try {
      const res = await postJSON("/api/utils/urlinfo", { url: inputUrl });
      if (res.ok) {
        const data = await res.json();
        setFormData((current) => ({
          ...current,
          title: current.title || data.title || "",
          desc: current.desc || data.desc || "",
          icon: current.icon || data.icon || "",
        }));
      }
    } catch (err) {
      setError(err);
    } finally {
      setIsLoadingInfo(false);
    }
  }

  function updateField(e) {
    const { name } = e.target;
    let { value } = e.target;
    if (name === "categoryId") value = Number(value);
    setFormData((current) => ({ ...current, [name]: value }));
  }

  function autocompleteUrl() {
    if (url && !url.startsWith("http")) setUrl("https://" + url);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setIsSending(true);
    setError(undefined);

    const submitData = {
      ...formData,
      url,
      text: initialShareData.text,
      categoryId: formData.newCategoryName.trim()
        ? undefined
        : formData.categoryId || undefined,
      newCategoryName: formData.newCategoryName.trim() || undefined,
    };

    try {
      const res = await postJSON("/api/share", submitData);
      const data = await res.json();
      if (res.ok) {
        setComplete(data);
        notify(
          data.duplicate ? "Already saved" : "Saved",
          data.duplicate
            ? "This link is already in NeonLink."
            : "The link was saved to NeonLink.",
          "info"
        );
      } else {
        setError(data);
      }
    } catch (err) {
      setError(err);
    } finally {
      setIsSending(false);
    }
  }

  if (complete) {
    return (
      <Page>
        <div className="flex justify-center w-full">
          <div className="md:w-1/2 px-3 w-full flex flex-col gap-4 my-6 rounded bg-white/90 p-5 shadow dark:bg-gray-900/90 dark:text-white">
            <div>
              <h1 className="text-2xl font-light">
                {complete.duplicate
                  ? "Already in NeonLink"
                  : "Saved to NeonLink"}
              </h1>
              <p className="mt-2 break-words text-sm text-gray-700 dark:text-gray-300">
                {url}
              </p>
            </div>
            <div className="flex flex-wrap justify-end gap-2">
              <button
                className={BUTTON_BASE_CLASS + "bg-gray-600 hover:bg-gray-500"}
                type="button"
                onClick={() => window.close()}
              >
                Close
              </button>
              <button
                className={BUTTON_BASE_CLASS}
                type="button"
                onClick={() => navigate("/")}
              >
                Open NeonLink
              </button>
            </div>
          </div>
        </div>
      </Page>
    );
  }

  return (
    <Page>
      <div className="flex justify-center w-full">
        <form
          className="md:w-1/2 px-3 w-full flex flex-col gap-3 my-3"
          onSubmit={handleSubmit}
        >
          <input
            className="w-full rounded border focus:outline-none focus:ring-cyan-600 focus:ring px-4 py-2 bg-white/80 dark:bg-gray-900/70 dark:text-white"
            type="url"
            name="url"
            placeholder="URL"
            onChange={(e) => setUrl(e.target.value)}
            onBlur={autocompleteUrl}
            value={url}
            required
            autoFocus={!url}
          />
          <input
            className="w-full rounded border focus:outline-none focus:ring-cyan-600 focus:ring px-4 py-2 bg-white/80 dark:bg-gray-900/70 dark:text-white"
            type="text"
            placeholder={isLoadingInfo ? "Reading title..." : "Title"}
            name="title"
            value={formData.title}
            onChange={updateField}
          />
          <textarea
            className="w-full rounded border focus:outline-none focus:ring-cyan-600 focus:ring px-4 py-2 bg-white/80 dark:bg-gray-900/70 dark:text-white"
            placeholder="Description"
            name="desc"
            value={formData.desc}
            onChange={updateField}
          />
          <TagInput
            tags={formData.tags}
            setTags={(tags) => setFormData((current) => ({ ...current, tags }))}
          />
          <select
            className="w-full rounded border focus:outline-none focus:ring-cyan-600 focus:ring px-4 py-2 bg-white/80 dark:bg-gray-900/70 dark:text-white"
            name="categoryId"
            value={formData.categoryId}
            onChange={updateField}
            disabled={Boolean(formData.newCategoryName.trim())}
          >
            <option className="dark:text-white dark:bg-gray-900" value={0}>
              No group
            </option>
            {categories.map((category) => (
              <option
                className="dark:text-white dark:bg-gray-900"
                key={category.id}
                value={category.id}
              >
                {category.name}
              </option>
            ))}
          </select>
          <div className="flex gap-2">
            <input
              className="w-full rounded border focus:outline-none focus:ring-cyan-600 focus:ring px-4 py-2 bg-white/80 dark:bg-gray-900/70 dark:text-white"
              type="text"
              placeholder="New group name"
              name="newCategoryName"
              value={formData.newCategoryName}
              onChange={updateField}
            />
            <input
              className="h-10 w-14 flex-none rounded border bg-white/80 p-1 dark:bg-gray-900/70"
              type="color"
              name="newCategoryColor"
              value={formData.newCategoryColor}
              onChange={updateField}
              aria-label="New group color"
            />
          </div>
          <div className="flex justify-between gap-3">
            <div className="text-red-600">{error?.message || ""}</div>
            <button
              className={BUTTON_BASE_CLASS + "min-w-24"}
              type="submit"
              disabled={isSending || !url}
            >
              {isSending ? "Saving..." : "Save"}
            </button>
          </div>
        </form>
      </div>
    </Page>
  );
}
