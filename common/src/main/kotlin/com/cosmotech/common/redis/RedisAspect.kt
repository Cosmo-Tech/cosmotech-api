// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.redis

import com.cosmotech.common.utils.sanitizeForRedis
import com.cosmotech.common.utils.toSecurityConstraintQuery
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component

@Aspect
@Component
class RedisAspect {

  @Around("@annotation(com.redis.om.spring.annotations.Query)")
  fun sanitizeQueryParameters(joinPoint: ProceedingJoinPoint): Any? {
    val methodSignature = joinPoint.signature as MethodSignature
    applyAnnotation<Sanitize>(methodSignature) {
      val parameter = joinPoint.args[it]
      if (parameter is String) {
        joinPoint.args[it] = parameter.sanitizeForRedis()
      }
    }
    applyAnnotation<SecurityConstraint>(methodSignature) {
      val parameter = joinPoint.args[it]
      if (parameter is String) {
        joinPoint.args[it] = parameter.toSecurityConstraintQuery()
      }
    }
    return joinPoint.proceed(joinPoint.args)
  }

  private inline fun <reified T> applyAnnotation(
      methodSignature: MethodSignature,
      applyLambda: (Int) -> Unit,
  ) {
    val parameterAnnotations = methodSignature.method.parameterAnnotations
    val annotatedParamIndexes =
        parameterAnnotations.map { it.any { annotation -> annotation is T } }
    annotatedParamIndexes.forEachIndexed { index, toApply ->
      if (toApply) {
        applyLambda(index)
      }
    }
  }
}
