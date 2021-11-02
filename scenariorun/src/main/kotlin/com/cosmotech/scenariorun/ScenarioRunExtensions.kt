// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRun

/**
 * PROD-8473 : containers listed in a ScenarioRun return environment variables, which are likely to
 * contain sensitive information, like secrets or connection strings.
 *
 * For security purposes, this extension method purposely hides such data, and aims at being used by
 * public-facing API endpoints.
 *
 * We might manually see the actual scenario run containers either via the Workflow Service, or
 * right by inspecting the Kubernetes Cluster.
 */
internal fun ScenarioRun?.withoutSensitiveData(): ScenarioRun? = this?.copy(containers = null)
