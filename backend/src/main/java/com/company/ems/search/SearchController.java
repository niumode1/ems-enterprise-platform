package com.company.ems.search;

import com.company.ems.common.ApiResponse;
import com.company.ems.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ApiResponse<SearchDtos.Response> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(searchService.search(keyword, types, page, size, user));
    }
}
