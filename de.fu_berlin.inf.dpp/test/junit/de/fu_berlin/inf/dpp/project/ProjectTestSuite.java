package de.fu_berlin.inf.dpp.project;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import de.fu_berlin.inf.dpp.project.internal.ProjectInternalTestSuite;

@RunWith(Suite.class)
@Suite.SuiteClasses({/*
                      * ResourceActivityFilterPluginTest.class,
                      * SharedProjectPluginTest.class,PlugIn Tests
                      */
SharedProjectUpdatableValuePluginTest.class, ProjectInternalTestSuite.class })
public class ProjectTestSuite {
    // the class remains completely empty,
    // being used only as a holder for the above annotations
}