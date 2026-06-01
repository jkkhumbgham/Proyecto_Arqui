package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.puj.ui.service.ApiClientService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Named
@RequestScoped
public class SearchBean {

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    public static class SearchResult {
        private final String id, title, description, category, coverUrl;
        public SearchResult(String id, String title, String description,
                            String category, String coverUrl) {
            this.id = id; this.title = title; this.description = description;
            this.category = category; this.coverUrl = coverUrl;
        }
        public String getId()          { return id; }
        public String getTitle()       { return title; }
        public String getDescription() { return description; }
        public String getCategory()    { return category; }
        public String getCoverUrl()    { return coverUrl; }
    }

    private String            q          = "";
    private String            category   = "";
    private String            sort       = "newest";
    private int               page       = 0;
    private int               size       = 12;
    private long              total      = 0;
    private int               totalPages = 0;
    private List<SearchResult> results   = new ArrayList<>();
    private boolean           searched   = false;

    @PostConstruct
    public void load() {
        var params = jakarta.faces.context.FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        q        = params.getOrDefault("q", "");
        category = params.getOrDefault("category", "");
        sort     = params.getOrDefault("sort", "newest");
        try { page = Integer.parseInt(params.getOrDefault("page", "0")); } catch (Exception e) { page = 0; }

        if (!q.isBlank() || !category.isBlank()) {
            doSearch();
        }
    }

    public String search() {
        return "courses?faces-redirect=true&q=" + encodeQ(q)
                + "&category=" + encodeQ(category)
                + "&sort=" + sort
                + "&page=0";
    }

    private void doSearch() {
        searched = true;
        try {
            String url = COURSE_URL + "/api/v1/search?q=" + encodeQ(q)
                    + "&category=" + encodeQ(category)
                    + "&sort=" + sort
                    + "&page=" + page
                    + "&size=" + size;
            HttpResponse<String> resp = api.getPublic(url);
            if (resp.statusCode() == 200) {
                JsonNode root = api.readTree(resp.body());
                total      = root.path("total").asLong();
                totalPages = root.path("totalPages").asInt();
                root.path("data").forEach(n -> results.add(new SearchResult(
                        n.path("id").asText(),
                        n.path("title").asText(),
                        n.path("description").asText(""),
                        n.path("category").asText(""),
                        n.path("coverUrl").asText("")
                )));
            }
        } catch (Exception ignored) {}
    }

    private String encodeQ(String s) {
        if (s == null || s.isBlank()) return "";
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    public String  getQ()          { return q; }
    public void    setQ(String q)  { this.q = q; }
    public String  getCategory()   { return category; }
    public void    setCategory(String c) { this.category = c; }
    public String  getSort()       { return sort; }
    public void    setSort(String s) { this.sort = s; }
    public int     getPage()       { return page; }
    public void    setPage(int p)  { this.page = p; }
    public long    getTotal()      { return total; }
    public int     getTotalPages() { return totalPages; }
    public boolean isSearched()    { return searched; }
    public List<SearchResult> getResults() { return results; }
    public boolean isHasResults()  { return !results.isEmpty(); }
}
