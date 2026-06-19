package com.lextr.semanticlayer.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class ControllerExecutionTimeAspect {

    private static final Logger logger = LoggerFactory.getLogger(ControllerExecutionTimeAspect.class);

    @Around("@within(restController)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, RestController restController) throws Throwable {
        long startTime = System.nanoTime();
        Object result = joinPoint.proceed();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        logger.info(
                "Handled request {} via {}.{} in {} ms",
                currentRequestSummary(),
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                elapsedMillis
        );
        return result;
    }

    private static String currentRequestSummary() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return "n/a";
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        return request.getMethod() + " " + request.getRequestURI();
    }
}
