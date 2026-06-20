package com.lextr.semanticlayer.logging;

import com.lextr.semanticlayer.api.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Aspect
@Component
public class ControllerExecutionTimeAspect {

    private static final Logger logger = LoggerFactory.getLogger(ControllerExecutionTimeAspect.class);
    private static final String NOT_AVAILABLE = "n/a";
    private static final Pattern CONTROLLER_PATTERN = Pattern.compile("(^|\\.)api(\\.|$)|Controller$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVICE_PATTERN = Pattern.compile("(^|\\.)service(s)?(\\.|$)|Service(Impl)?$|Client(Configuration)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("(^|\\.)dao(\\.|$)|Repository$|Dao$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPONENT_PATTERN = Pattern.compile("(^|\\.)util(s)?(\\.|$)|Util$|Component$", Pattern.CASE_INSENSITIVE);

    @Around("""
            (
                within(@org.springframework.web.bind.annotation.RestController *) ||
                within(@org.springframework.stereotype.Service *) ||
                within(@org.springframework.stereotype.Repository *) ||
                within(@org.springframework.stereotype.Component *)
            ) &&
            !within(org.springframework..*) &&
            !within(org.springframework.cloud..*) &&
            !within(com.lextr.semanticlayer.logging..*) &&
            !within(java..*) &&
            !within(jdk..*)
            """)
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetClass = targetClass(joinPoint);
        if (shouldSkip(targetClass)) {
            return joinPoint.proceed();
        }

        String layer = layer(targetClass);
        String requestSummary = currentRequestSummary();
        long startTime = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsedMillis = elapsedMillis(startTime);
            logSuccess(layer, targetClass, joinPoint, requestSummary, elapsedMillis);
            return result;
        } catch (Throwable throwable) {
            long elapsedMillis = elapsedMillis(startTime);
            logFailure(layer, targetClass, joinPoint, requestSummary, elapsedMillis, throwable);
            throw throwable;
        }
    }

    private static String currentRequestSummary() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return NOT_AVAILABLE;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        return request.getMethod() + " " + request.getRequestURI();
    }

    private static Class<?> targetClass(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target != null) {
            return target.getClass();
        }
        return joinPoint.getSignature().getDeclaringType();
    }

    private static boolean shouldSkip(Class<?> targetClass) {
        return targetClass == null
                || ApiExceptionHandler.class.isAssignableFrom(targetClass)
                || ControllerExecutionTimeAspect.class.isAssignableFrom(targetClass)
                || !isLoggable(targetClass);
    }

    private static boolean isLoggable(Class<?> targetClass) {
        return AnnotatedElementUtils.hasAnnotation(targetClass, RestController.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, Service.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, Repository.class)
                || CONTROLLER_PATTERN.matcher(targetClass.getName()).find()
                || SERVICE_PATTERN.matcher(targetClass.getName()).find()
                || REPOSITORY_PATTERN.matcher(targetClass.getName()).find()
                || COMPONENT_PATTERN.matcher(targetClass.getName()).find();
    }

    private static String layer(Class<?> targetClass) {
        if (AnnotatedElementUtils.hasAnnotation(targetClass, RestController.class)) {
            return "controller";
        }
        if (AnnotatedElementUtils.hasAnnotation(targetClass, Service.class)) {
            return "service";
        }
        if (AnnotatedElementUtils.hasAnnotation(targetClass, Repository.class)) {
            return "repository";
        }
        if (AnnotatedElementUtils.hasAnnotation(targetClass, Component.class)) {
            return "component";
        }

        String className = targetClass.getName();
        if (CONTROLLER_PATTERN.matcher(className).find()) {
            return "controller";
        }
        if (SERVICE_PATTERN.matcher(className).find()) {
            return "service";
        }
        if (REPOSITORY_PATTERN.matcher(className).find()) {
            return "repository";
        }
        if (COMPONENT_PATTERN.matcher(className).find()) {
            return "component";
        }
        return "application";
    }

    private static long elapsedMillis(long startTime) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
    }

    private static void logSuccess(String layer,
                                   Class<?> targetClass,
                                   ProceedingJoinPoint joinPoint,
                                   String requestSummary,
                                   long elapsedMillis) {
        if ("controller".equals(layer)) {
            logger.info(
                    "Handled request {} via {}.{} in {} ms",
                    requestSummary,
                    targetClass.getSimpleName(),
                    joinPoint.getSignature().getName(),
                    elapsedMillis
            );
            return;
        }

        logger.info(
                "Completed {} {}.{} in {} ms{}",
                layer,
                targetClass.getSimpleName(),
                joinPoint.getSignature().getName(),
                elapsedMillis,
                requestContextSuffix(requestSummary)
        );
    }

    private static void logFailure(String layer,
                                   Class<?> targetClass,
                                   ProceedingJoinPoint joinPoint,
                                   String requestSummary,
                                   long elapsedMillis,
                                   Throwable throwable) {
        if ("controller".equals(layer)) {
            return;
        }

        logger.warn(
                "Failed {} {}.{} in {} ms{} exception={} message={}",
                layer,
                targetClass.getSimpleName(),
                joinPoint.getSignature().getName(),
                elapsedMillis,
                requestContextSuffix(requestSummary),
                throwable.getClass().getSimpleName(),
                throwable.getMessage()
        );
    }

    private static String requestContextSuffix(String requestSummary) {
        if (NOT_AVAILABLE.equals(requestSummary)) {
            return "";
        }
        return " for request " + requestSummary;
    }
}
