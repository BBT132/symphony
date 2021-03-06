/*
 * Copyright (c) 2012-2016, b3log.org & hacpai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.b3log.symphony.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.After;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.freemarker.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Stopwatchs;
import org.b3log.latke.util.Strings;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.advice.AnonymousViewCheck;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchEndAdvice;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchStartAdvice;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.TimelineMgmtService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.Filler;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

/**
 * Index processor.
 *
 * <ul>
 * <li>Shows index (/), GET</li>
 * <li>Shows recent articles (/recent), GET</li>
 * <li>Shows hot articles (/hot), GET</li>
 * <li>Shows perfect articles (/perfect), GET</li>
 * <li>Shows about (/about), GET</li>
 * <li>Shows b3log (/b3log), GET</li>
 * <li>Shows kill browser (/kill-browser), GET</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 1.9.2.20, Oct 26, 2016
 * @since 0.2.0
 */
@RequestProcessor
public class IndexProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(IndexProcessor.class.getName());

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Filler.
     */
    @Inject
    private Filler filler;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Timeline management service.
     */
    @Inject
    private TimelineMgmtService timelineMgmtService;

    /**
     * Shows index.
     *
     * @param context the specified context
     * @param request the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = {"", "/"}, method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class})
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showIndex(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);

//        final List<JSONObject> hotArticles = articleQueryService.getIndexHotArticles(avatarViewMode);
//        dataModel.put(Common.HOT_ARTICLES, hotArticles);
        final List<JSONObject> recentArticles = articleQueryService.getIndexRecentArticles(avatarViewMode);
        dataModel.put(Common.RECENT_ARTICLES, recentArticles);

        final List<JSONObject> perfectArticles = articleQueryService.getIndexPerfectArticles(avatarViewMode);
        dataModel.put(Common.PERFECT_ARTICLES, perfectArticles);

        final List<JSONObject> timelines = timelineMgmtService.getTimelines();
        dataModel.put(Common.TIMELINES, timelines);

        dataModel.put(Common.SELECTED, Common.INDEX);

        filler.fillHeaderAndFooter(request, response, dataModel);
        filler.fillIndexTags(dataModel);
    }

    /**
     * Shows recent articles.
     *
     * @param context the specified context
     * @param request the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = {"/recent", "/recent/hot", "/recent/good", "/recent/reply"}, method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showRecent(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("recent.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        String pageNumStr = request.getParameter("p");
        if (Strings.isEmptyOrNull(pageNumStr) || !Strings.isNumeric(pageNumStr)) {
            pageNumStr = "1";
        }

        final int pageNum = Integer.valueOf(pageNumStr);
        int pageSize = Symphonys.getInt("indexArticlesCnt");
        final JSONObject user = userQueryService.getCurrentUser(request);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
        }

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);

        String sortModeStr = StringUtils.substringAfter(request.getRequestURI(), "/recent");
        int sortMode;
        switch (sortModeStr) {
            case "":
                sortMode = 0;

                break;
            case "/hot":
                sortMode = 1;

                break;
            case "/good":
                sortMode = 2;

                break;
            case "/reply":
                sortMode = 3;

                break;
            default:
                sortMode = 0;
        }

        final JSONObject result = articleQueryService.getRecentArticles(avatarViewMode, sortMode, pageNum, pageSize);
        final List<JSONObject> allArticles = (List<JSONObject>) result.get(Article.ARTICLES);

        dataModel.put(Article.ARTICLE_T_STICK_CHECK, true);
        dataModel.put(Common.SELECTED, Common.RECENT);

        final List<JSONObject> stickArticles = new ArrayList<>();

        final Iterator<JSONObject> iterator = allArticles.iterator();
        while (iterator.hasNext()) {
            final JSONObject article = iterator.next();

            final boolean stick = article.optInt(Article.ARTICLE_T_STICK_REMAINS) > 0;
            article.put(Article.ARTICLE_T_IS_STICK, stick);

            if (stick) {
                stickArticles.add(article);
                iterator.remove();
            }
        }

        dataModel.put(Common.STICK_ARTICLES, stickArticles);
        dataModel.put(Common.LATEST_ARTICLES, allArticles);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);

        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        filler.fillHeaderAndFooter(request, response, dataModel);

        filler.fillRandomArticles(avatarViewMode, dataModel);
        filler.fillSideHotArticles(avatarViewMode, dataModel);
        filler.fillSideTags(dataModel);
        filler.fillLatestCmts(dataModel);

        dataModel.put(Common.CURRENT, StringUtils.substringAfter(request.getRequestURI(), "/recent"));
    }

    /**
     * Shows hot articles.
     *
     * @param context the specified context
     * @param request the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/hot", method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showHotArticles(final HTTPRequestContext context,
            final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("hot.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        int pageSize = Symphonys.getInt("indexArticlesCnt");

        final JSONObject user = userQueryService.getCurrentUser(request);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
        }

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);

        final List<JSONObject> indexArticles = articleQueryService.getHotArticles(avatarViewMode, pageSize);
        dataModel.put(Common.INDEX_ARTICLES, indexArticles);

        dataModel.put(Common.SELECTED, Common.HOT);

        Stopwatchs.start("Fills");
        try {
            filler.fillHeaderAndFooter(request, response, dataModel);
            if (!(Boolean) dataModel.get(Common.IS_MOBILE)) {
                filler.fillRandomArticles(avatarViewMode, dataModel);
            }
            filler.fillSideHotArticles(avatarViewMode, dataModel);
            filler.fillSideTags(dataModel);
            filler.fillLatestCmts(dataModel);
        } finally {
            Stopwatchs.end();
        }
    }

    /**
     * Shows perfect articles.
     *
     * @param context the specified context
     * @param request the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/perfect", method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showPerfectArticles(final HTTPRequestContext context,
            final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("perfect.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        String pageNumStr = request.getParameter("p");
        if (Strings.isEmptyOrNull(pageNumStr) || !Strings.isNumeric(pageNumStr)) {
            pageNumStr = "1";
        }

        final int pageNum = Integer.valueOf(pageNumStr);
        int pageSize = Symphonys.getInt("indexArticlesCnt");
        final JSONObject user = userQueryService.getCurrentUser(request);
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
        }

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);

        final JSONObject result = articleQueryService.getPerfectArticles(avatarViewMode, pageNum, pageSize);
        final List<JSONObject> perfectArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        dataModel.put(Common.PERFECT_ARTICLES, perfectArticles);

        dataModel.put(Common.SELECTED, Common.PERFECT);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);

        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        filler.fillHeaderAndFooter(request, response, dataModel);
        filler.fillRandomArticles(avatarViewMode, dataModel);
        filler.fillSideHotArticles(avatarViewMode, dataModel);
        filler.fillSideTags(dataModel);
        filler.fillLatestCmts(dataModel);
    }

    /**
     * Shows about.
     *
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/about", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showAbout(final HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", "https://hacpai.com/article/1440573175609");
        response.flushBuffer();
    }

    /**
     * Shows b3log.
     *
     * @param context the specified context
     * @param request the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/b3log", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showB3log(final HTTPRequestContext context,
            final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("b3log.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        filler.fillHeaderAndFooter(request, response, dataModel);

        final int avatarViewMode = (int) request.getAttribute(UserExt.USER_AVATAR_VIEW_MODE);

        filler.fillRandomArticles(avatarViewMode, dataModel);
        filler.fillSideHotArticles(avatarViewMode, dataModel);
        filler.fillSideTags(dataModel);
        filler.fillLatestCmts(dataModel);
    }

    /**
     * Shows kill browser page with the specified context.
     *
     * @param context the specified context
     * @param request the specified HTTP servlet request
     * @param response the specified HTTP servlet response
     */
    @RequestProcessing(value = "/kill-browser", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showKillBrowser(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        renderer.setTemplateName("kill-browser.ftl");
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = renderer.getDataModel();

        final Map<String, String> langs = langPropsService.getAll(Locales.getLocale());

        dataModel.putAll(langs);
        Keys.fillRuntime(dataModel);
        filler.fillMinified(dataModel);
    }
}
