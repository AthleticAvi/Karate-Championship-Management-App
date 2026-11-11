package com.management.web;

import com.management.account.web.AccountController;
import com.management.athlete.web.AthleteController;
import com.management.kumitegame.web.KumiteGameController;
import com.management.referee.web.RefereeController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The one place the API version is named: every controller in a feature's web package answers under
 * it.
 *
 * <p>Scoped to those packages, so the actuator, the error path and any library controller stay
 * where they are. A constant and not a setting, because a client depends on the path: it is a
 * contract, not something a deployment tunes.
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfiguration implements WebMvcConfigurer {

  public static final String PREFIX = "/api/v1";

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(
        PREFIX,
        HandlerTypePredicate.forBasePackageClass(
            KumiteGameController.class,
            AthleteController.class,
            RefereeController.class,
            AccountController.class));
  }
}
