/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.maven;

import com.jcabi.log.Logger;
import com.yegor256.xsline.TrDefault;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.cactoos.iterable.Filtered;
import org.cactoos.map.MapEntry;
import org.cactoos.map.MapOf;
import org.eolang.maven.optimization.OptSpy;
import org.eolang.maven.optimization.OptTrain;
import org.eolang.maven.optimization.Optimization;
import org.eolang.maven.tojos.ForeignTojo;
import org.eolang.parser.ParsingTrain;

/**
 * Optimize XML files.
 *
 * @since 0.1
 */
@Mojo(
    name = "optimize",
    defaultPhase = LifecyclePhase.PROCESS_SOURCES,
    threadSafe = true
)
public final class OptimizeMojo extends SafeMojo {

    /**
     * The directory where to transpile to.
     */
    public static final String DIR = "2-optimize";

    /**
     * Subdirectory for optimized cache.
     */
    static final String OPTIMIZED = "optimized";

    /**
     * The directory where to place intermediary files.
     */
    static final String STEPS = "2-optimization-steps";

    /**
     * The map with directories of OptimizeMojo.
     * @checkstyle DiamondOperatorCheck (10 lines)
     */
    private static final Map<String, String> DIRECTORIES = new MapOf<String, String>(
        new MapEntry<>(OptimizationFolder.TARGET.key(), OptimizeMojo.DIR),
        new MapEntry<>(OptimizationFolder.CACHE.key(), OptimizeMojo.OPTIMIZED)
    );

    /**
     * Track optimization steps into intermediate XML files?
     *
     * @checkstyle MemberNameCheck (7 lines)
     * @since 0.24.0
     */
    @SuppressWarnings("PMD.LongVariable")
    @Parameter(property = "eo.trackOptimizationSteps", required = true, defaultValue = "false")
    private boolean trackOptimizationSteps;

    /**
     * Whether we should fail on error.
     *
     * @checkstyle MemberNameCheck (7 lines)
     * @since 0.23.0
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Parameter(
        property = "eo.failOnError",
        defaultValue = "true")
    private boolean failOnError = true;

    /**
     * Whether we should fail on warn.
     *
     * @checkstyle MemberNameCheck (10 lines)
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Parameter(
        property = "eo.failOnWarning",
        required = true,
        defaultValue = "false"
    )
    private boolean failOnWarning;

    @Override
    public void exec() throws IOException {
        final Collection<ForeignTojo> tojos = this.scopedTojos().withXmir();
        final int total = new OptimizedTojos(
            new Filtered<>(
                ForeignTojo::notOptimized,
                tojos
            ),
            this.optimization(),
            new OptimizationTask(
                new MapOf<String, Path>(
                    new MapEntry<>(OptimizationFolder.TARGET.key(), this.targetDir.toPath()),
                    new MapEntry<>(OptimizationFolder.CACHE.key(), this.cache)
                ),
                OptimizeMojo.DIRECTORIES,
                ForeignTojo::withOptimized,
                ForeignTojo::xmir
            )
        ).count();
        if (total > 0) {
            Logger.info(
                this,
                "Optimized %d out of %d XMIR program(s)", total,
                tojos.size()
            );
        } else {
            Logger.debug(this, "No XMIR programs out of %d optimized", tojos.size());
        }
    }

    /**
     * Common optimization for all tojos.
     *
     * @return Optimization for all tojos.
     */
    private Optimization optimization() {
        Optimization opt;
        if (this.trackOptimizationSteps) {
            opt = new OptSpy(
                new ParsingTrain(),
                this.targetDir.toPath().resolve(OptimizeMojo.STEPS)
            );
        } else {
            opt = new OptTrain(new ParsingTrain());
        }
        if (this.failOnError) {
            opt = new OptTrain(opt, "/org/eolang/parser/fail-on-errors.xsl");
        }
        if (this.failOnWarning) {
            opt = new OptTrain(opt, "/org/eolang/parser/fail-on-warnings.xsl");
        }
        if (this.failOnError) {
            opt = new OptTrain(opt, "/org/eolang/parser/fail-on-critical.xsl");
        } else {
            opt = new OptTrain(opt, new TrDefault<>());
        }
        return opt;
    }
}
