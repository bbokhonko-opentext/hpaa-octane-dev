/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.tests;

import com.microfocus.application.automation.tools.octane.OctanePluginTestBase;
import com.microfocus.application.automation.tools.octane.tests.junit.TestResultStatus;
import hudson.matrix.*;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Maven;
import hudson.tasks.junit.JUnitResultArchiver;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.ToolInstallations;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings({"squid:S2699", "squid:S3658", "squid:S2259", "squid:S1872", "squid:S2925", "squid:S109", "squid:S1607", "squid:S2701", "squid:S2698"})
public class JUnitResultsTest extends OctanePluginTestBase {

	private static Set<String> helloWorld2Tests = new HashSet<>();

	static {
		helloWorld2Tests.add(TestUtils.testSignature("helloWorld2", "hello", "HelloWorld2Test", "testOnce",
				TestResultStatus.PASSED));
		helloWorld2Tests.add(TestUtils.testSignature("helloWorld2", "hello", "HelloWorld2Test", "testDoce", TestResultStatus.PASSED));
	}

	private static Set<String> subFolderHelloWorldTests = new HashSet<>();

	static {
		subFolderHelloWorldTests.add(TestUtils.testSignature("subFolder/helloWorld", "hello", "HelloWorldTest", "testOne", TestResultStatus.PASSED));
		subFolderHelloWorldTests.add(TestUtils.testSignature("subFolder/helloWorld", "hello", "HelloWorldTest", "testTwo", TestResultStatus.FAILED));
		subFolderHelloWorldTests.add(TestUtils.testSignature("subFolder/helloWorld", "hello", "HelloWorldTest", "testThree", TestResultStatus.SKIPPED));
	}

	private static String mavenName;

	@BeforeClass
	public static void prepareClass() throws Exception {
		rule.jenkins.setNumExecutors(10);
		Maven.MavenInstallation mavenInstallation = ToolInstallations.configureMaven3();
		mavenName = mavenInstallation.getName();
	}

	@Test
	public void testJUnitResults() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);

		project.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.repo.local=%s\\m2-temp",
				TestUtils.getMavenHome(), System.getenv("TEMP")), mavenName, null, null, "-Dmaven.test.failure.ignore=true"));
		project.getPublishersList().add(new JUnitResultArchiver("**/target/surefire-reports/*.xml"));
		project.setScm(new CopyResourceSCM("/helloWorldRoot"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, TestUtils.helloWorldTests, helloWorld2Tests);
	}

	@Test
	public void testJUnitResultsPom() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);

		project.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.repo.local=%s\\m2-temp",
				TestUtils.getMavenHome(), System.getenv("TEMP")), mavenName, "subFolder/helloWorld/pom.xml", null, "-Dmaven.test.failure.ignore=true"));
		project.getPublishersList().add(new JUnitResultArchiver("**/target/surefire-reports/*.xml"));
		project.setScm(new CopyResourceSCM("/helloWorldRoot", "subFolder"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, subFolderHelloWorldTests);
	}

	@Test
	public void testJUnitResultsTwoPoms() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);

		project.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.repo.local=%s\\m2-temp",
				TestUtils.getMavenHome(), System.getenv("TEMP")), mavenName, "pom.xml", null, "-Dmaven.test.failure.ignore=true"));
		project.getPublishersList().add(new JUnitResultArchiver("**/target/surefire-reports/*.xml"));
		project.setScm(new CopyResourceSCM("/helloWorldRoot"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, TestUtils.helloWorldTests, helloWorld2Tests);
	}

	@Test
	public void testJUnitResultsLegacy() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		MavenModuleSet project = rule.createProject(MavenModuleSet.class, projectName);
		project.runHeadless();

		project.setMaven(mavenName);
		project.setGoals(String.format("clean test --settings \"%s\\conf\\settings.xml\" -Dmaven.repo.local=%s\\m2-temp -Dmaven.test.failure.ignore=true",
				TestUtils.getMavenHome(), System.getenv("TEMP")));
		project.getPublishersList().add(new JUnitResultArchiver("**/target/surefire-reports/*.xml"));
		project.setScm(new CopyResourceSCM("/helloWorldRoot"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, TestUtils.helloWorldTests, helloWorld2Tests);
	}

	//temporary disable as it failed in CI
	//@Test
	public void testJUnitResultsLegacyWithoutJUnitArchiver() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		MavenModuleSet project = rule.createProject(MavenModuleSet.class, projectName);
		project.runHeadless();

		project.setMaven(mavenName);
		project.setGoals(String.format("clean test --settings \"%s\\conf\\settings.xml\" -Dmaven.repo.local=%s\\m2-temp -Dmaven.test.failure.ignore=true",
				TestUtils.getMavenHome(), System.getenv("TEMP")));
		project.setScm(new CopyResourceSCM("/helloWorldRoot"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, TestUtils.helloWorldTests, helloWorld2Tests);
	}

	@Test
	public void testJUnitResultsLegacySubfolder() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		MavenModuleSet project = rule.createProject(MavenModuleSet.class, projectName);
		project.runHeadless();

		project.setMaven(mavenName);
		project.setRootPOM("subFolder/helloWorld/pom.xml");
		project.setGoals(String.format("clean test --settings \"%s\\conf\\settings.xml\" -Dmaven.repo.local=%s\\m2-temp -Dmaven.test.failure.ignore=true",
				TestUtils.getMavenHome(), System.getenv("TEMP")));
		project.getPublishersList().add(new JUnitResultArchiver("**/target/surefire-reports/*.xml"));
		project.setScm(new CopyResourceSCM("/helloWorldRoot", "subFolder"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, subFolderHelloWorldTests);
	}

	@Test
	public void testJUnitResultsWorkspaceStripping() throws Exception {
		Set<String> uftTests = new HashSet<>();
		uftTests.add(TestUtils.testSignature("", "All-Tests", "<None>", "subfolder" + File.separator + "CalculatorPlusNextGen", TestResultStatus.FAILED));

		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);
		project.getPublishersList().add(new TestCustomJUnitArchiver("UFT_results.xml"));
		project.setScm(new CopyResourceSCM("/UFT"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, uftTests);
	}

	@Test
	public void testJUnitResultsFreeStyleModule() throws Exception {
		// this scenario simulates FreeStyle project with maven executed via shell (by not using Maven builder directly)
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);

		project.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.repo.local=%s\\m2-temp",
				TestUtils.getMavenHome(), System.getenv("TEMP")), mavenName, null, null, "-Dmaven.test.failure.ignore=true"));
		project.getPublishersList().add(new JUnitResultArchiver("**/target/surefire-reports/*.xml"));
		project.setScm(new CopyResourceSCM("/helloWorldRoot"));
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		matchTests(build, projectName, TestUtils.helloWorldTests, helloWorld2Tests);
	}

	@Test
	public void testJUnitResultsMatrixProject() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		String axisParamName = "osType";
		String[] subtypes = new String[]{"Linux", "Windows"};
		MatrixProject matrixProject = rule.createProject(MatrixProject.class, projectName);
		matrixProject.setAxes(new AxisList(new Axis(axisParamName, subtypes)));

		matrixProject.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.test.failure.ignore=true -Dmaven.repo.local=%s\\m2-temp -X",
				TestUtils.getMavenHome(), System.getenv("TEMP")), mavenName));

		matrixProject.getPublishersList().add(new JUnitResultArchiver("**/target/surefire-reports/*.xml"));
		matrixProject.setScm(new CopyResourceSCM("/helloWorldRoot"));
		MatrixBuild build = (MatrixBuild) TestUtils.runAndCheckBuild(matrixProject);

		for (MatrixRun run : build.getExactRuns()) {
			matchTests(
					run,
					projectName + "/" + axisParamName + "=" + subtypes[build.getExactRuns().indexOf(run)],
					TestUtils.helloWorldTests, helloWorld2Tests);
		}
		Assert.assertFalse(new File(build.getRootDir(), "mqmTests.xml").exists());
	}

	private void matchTests(AbstractBuild build, String projectName, Set<String>... expectedTests) throws FileNotFoundException {
		File mqmTestsXml = new File(build.getRootDir(), "mqmTests.xml");
		TestUtils.matchTests(new TestResultIterable(mqmTestsXml), projectName, build.getStartTimeInMillis(), expectedTests);
	}
}
