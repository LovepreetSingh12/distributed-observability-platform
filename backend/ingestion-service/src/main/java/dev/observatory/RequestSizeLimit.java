package dev.observatory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class RequestSizeLimit extends OncePerRequestFilter {

    private static final int MAX_BODY_SIZE = 524288;

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        if (!req.getMethod().equals("POST")
                && !req.getMethod().equals("PUT")) {

            chain.doFilter(req, res);
            return;
        }

        byte[] body = req.getInputStream().readNBytes(MAX_BODY_SIZE + 1);

        if (body.length > MAX_BODY_SIZE) {
            res.sendError(413);
            return;
        }

        chain.doFilter(
            new HttpServletRequestWrapper(req) {

                @Override
                public ServletInputStream getInputStream() {

                    ByteArrayInputStream input =
                            new ByteArrayInputStream(body);

                    return new ServletInputStream() {

                        @Override
                        public int read() {
                            return input.read();
                        }

                        @Override
                        public boolean isFinished() {
                            return input.available() == 0;
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setReadListener(ReadListener listener) {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override
                public BufferedReader getReader() {

                    return new BufferedReader(
                            new InputStreamReader(
                                    getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
                }
            },
            res
        );
    }
}