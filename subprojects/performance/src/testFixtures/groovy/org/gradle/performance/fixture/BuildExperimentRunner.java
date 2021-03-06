/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.fixture;

import org.gradle.api.Action;
import org.gradle.integtests.fixtures.executer.GradleExecuter;
import org.gradle.performance.measure.MeasuredOperation;

import java.util.List;

public class BuildExperimentRunner {
    private final GCLoggingCollector gcCollector = new GCLoggingCollector();
    private final DataCollector dataCollector;
    private final GradleExecuterProvider executerProvider;
    private final OperationTimer timer = new OperationTimer();

    public BuildExperimentRunner(GradleExecuterProvider executerProvider) {
        this.executerProvider = executerProvider;
        MemoryInfoCollector memoryInfoCollector = new MemoryInfoCollector();
        memoryInfoCollector.setOutputFileName("build/totalMemoryUsed.txt");
        dataCollector = new CompositeDataCollector(memoryInfoCollector, gcCollector);
    }

    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        GradleInvocationSpec buildSpec = experiment.getInvocation();

        executerProvider.executer(buildSpec).withTasks().withArgument("--stop").run();

        try {
            for (int i = 0; i < experiment.getWarmUpCount(); i++) {
                System.out.println();
                System.out.println(String.format("Warm-up #%s", i + 1));
                runOnce(buildSpec, new MeasuredOperationList());
            }
            for (int i = 0; i < experiment.getInvocationCount(); i++) {
                System.out.println();
                System.out.println(String.format("Test run #%s", i + 1));
                runOnce(buildSpec, results);
            }
        } finally {
            if (buildSpec.getUseDaemon()) {
                executerProvider.executer(buildSpec).withTasks().withArgument("--stop").run();
            }
        }
    }

    private void runOnce(GradleInvocationSpec buildSpec, MeasuredOperationList results) {
        List<String> additionalGradleOpts = dataCollector.getAdditionalGradleOpts(buildSpec.getWorkingDirectory());
        GradleInvocationSpec effectiveSpec = buildSpec.withAdditionalGradleOpts(additionalGradleOpts);
        final GradleExecuter executer = executerProvider.executer(effectiveSpec);

        MeasuredOperation operation = timer.measure(new Action<MeasuredOperation>() {
            @Override
            public void execute(MeasuredOperation measuredOperation) {
                executer.run();
            }
        });

        if (operation.getException() == null) {
            dataCollector.collect(buildSpec.getWorkingDirectory(), operation);
        }

        results.add(operation);
    }
}
