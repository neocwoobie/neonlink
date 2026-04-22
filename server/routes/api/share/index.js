import { imgUrlToBase64 } from "../../../helpers/images.js";
import { parseHtml } from "../../../helpers/parsers.js";
import { appContext } from "../../../contexts/appContext.js";
import { appRequestsKeys } from "../../../contexts/appRequests.js";
import { requireSession } from "../../../logics/handlers.js";
import { requestHeadFromUrl } from "../../../logics/bookmarks.js";

const DEFAULT_CATEGORY_COLOR = "#06b6d4";

function extractUrlFromText(text = "") {
  return text.match(/https?:\/\/[^\s<>"']+/i)?.[0];
}

function normalizeUrl(url, text) {
  const candidate = (url || extractUrlFromText(text) || "").trim();
  if (!candidate) return "";

  const parsed = new URL(candidate);
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error(`Unsupported protocol ${parsed.protocol}`);
  }
  return parsed.toString();
}

function sanitizeTags(tags = []) {
  return [
    ...new Set(
      tags
        .map((tag) => tag.trim().toLocaleLowerCase())
        .filter((tag) => tag.length > 0)
    ),
  ].slice(0, 10);
}

function ensureTags(tags, userId) {
  for (const tag of tags) {
    if (!appContext.stores.tags.existsItemByName(tag, userId)) {
      appContext.stores.tags.addItem(tag, userId);
    }
  }
}

async function fetchUrlInfo(url) {
  try {
    const headHtml = await requestHeadFromUrl(url);
    return parseHtml(headHtml, url);
  } catch (error) {
    console.error(`Cannot read url info from ${url}`, error.message);
    return {};
  }
}

/**
 *
 * @param {import("fastify").FastifyInstance} fastify
 * @param {*} opts
 */
export default async function (fastify, opts) {
  fastify.post(
    "/",
    {
      preHandler: requireSession(true, true, false),
      schema: {
        body: {
          type: "object",
          properties: {
            url: { type: "string" },
            title: { type: "string" },
            text: { type: "string" },
            desc: { type: "string" },
            icon: { type: "string" },
            categoryId: { type: "number" },
            newCategoryName: { type: "string" },
            newCategoryColor: { type: "string" },
            tags: { type: "array", items: { type: "string" }, maxItems: 10 },
          },
        },
      },
    },
    async function (request, reply) {
      const user = appContext.request.get(appRequestsKeys.Session);
      const {
        title,
        text,
        desc,
        icon,
        categoryId,
        newCategoryName,
        newCategoryColor,
      } = request.body;

      let url;
      try {
        url = normalizeUrl(request.body.url, text);
      } catch (error) {
        throw fastify.httpErrors.notAcceptable(error.message);
      }

      if (!url) {
        throw fastify.httpErrors.notAcceptable("A shared URL is required.");
      }

      const existingBookmark = appContext.stores.bookmarks.getItemByUrl(
        user.userId,
        url
      );
      if (existingBookmark) {
        return {
          duplicate: true,
          bookmarkId: existingBookmark.id,
          categoryId: existingBookmark.categoryId,
        };
      }

      const urlInfo = !title || !desc || !icon ? await fetchUrlInfo(url) : {};
      const tags = sanitizeTags(request.body.tags);
      ensureTags(tags, user.userId);

      let selectedCategoryId = categoryId || undefined;
      const categoryName = newCategoryName?.trim();
      if (categoryName) {
        const existingCategory = appContext.stores.categories.getItemByName(
          categoryName,
          user.userId
        );
        selectedCategoryId =
          existingCategory?.id ||
          appContext.stores.categories.addItem(
            categoryName,
            newCategoryColor || DEFAULT_CATEGORY_COLOR,
            user.userId
          );
      }

      let bookmarkIcon = icon || urlInfo.icon || "";
      if (bookmarkIcon) {
        try {
          bookmarkIcon = await imgUrlToBase64(bookmarkIcon);
        } catch (error) {
          console.error(`Cannot read icon from ${bookmarkIcon}`, error.message);
          bookmarkIcon = "";
        }
      }

      const bookmarkTitle = (title || urlInfo.title || url).trim();
      const bookmarkDesc = desc || text || urlInfo.desc || "";
      const bookmarkId = appContext.stores.bookmarks.addItem(
        url,
        bookmarkTitle,
        bookmarkDesc,
        bookmarkIcon,
        selectedCategoryId,
        tags,
        user.userId
      );

      reply.statusCode = 201;
      return {
        duplicate: false,
        bookmarkId,
        categoryId: selectedCategoryId,
      };
    }
  );
}
