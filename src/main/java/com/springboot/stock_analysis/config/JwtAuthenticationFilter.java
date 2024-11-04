package com.springboot.stock_analysis.config;

import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.service.JwtService;
import com.springboot.stock_analysis.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final HandlerExceptionResolver handlerExceptionResolver;

  private final JwtService jwtService;
  private final UserService userService;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      UserService userService,
      HandlerExceptionResolver handlerExceptionResolver
  ) {
    this.jwtService = jwtService;
    this.userService = userService;
    this.handlerExceptionResolver = handlerExceptionResolver;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain
  ) throws ServletException, IOException {
    final String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      final String jwt = authHeader.substring(7);
      final String userName = jwtService.extractEamil(jwt);

      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

      if (userName != null && authentication == null) {
        UserDetails userDetails = this.userService.loadUserByUsername(userName);

        if (jwtService.isTokenValid(jwt, userDetails)) {
          UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
              userDetails,
              null,
              userDetails.getAuthorities()
          );

          authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authToken);
        }
      }

      filterChain.doFilter(request, response);
    }  catch (ExpiredJwtException e) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().write("Token Expired");
      response.getWriter().flush();
    }
    catch (Exception exception) {
      handlerExceptionResolver.resolveException(request, response, null, exception);
    }
  }
}