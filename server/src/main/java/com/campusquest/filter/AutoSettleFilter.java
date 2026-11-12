package com.campusquest.filter;

import com.campusquest.service.SettlementService;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * 惰性自动结算：每次 /api/* 请求前尝试结算已超时的待确认任务。
 */
public class AutoSettleFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        try {
            SettlementService.settleExpired();
        } catch (Exception e) {
            // 自动结算异常不能影响主流程
        }
        chain.doFilter(req, resp);
    }

    @Override
    public void destroy() {
    }
}
