/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.test.runtime.cpp;

import org.antlr.v4.test.runtime.*;
import org.stringtemplate.v4.ST;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.antlr.v4.test.runtime.BaseRuntimeTest.antlrOnString;
import static org.antlr.v4.test.runtime.BaseRuntimeTest.writeFile;
import static org.junit.Assert.assertTrue;

public class BaseCppTest extends BaseRuntimeTestSupport implements RuntimeTestSupport {
	@Override
	public String getLanguage() {
		return "Cpp";
	}

	protected String getPropertyPrefix() {
		return "antlr-" + getLanguage().toLowerCase();
	}

	private static Boolean runtimeBuiltOnce = false;

	private static String runtimePath;
	private static String runtimeSourcePath;
	private static String runtimeBinaryPath;
	private static String runtimeLibraryFileName;

	private static String compilerPath;
	private static String visualStudioVersion;
	private static String visualStudioProjectContent;
	private static String visualStudioPlatformToolset;

	static {
		initCompilerFileName();
		initRuntimePath();
		initBinaryPath();

		if (isWindows()) {
			visualStudioProjectContent = getTextFromResource("org/antlr/v4/test/runtime/helpers/CppVisualStudioProject.stg");
		}
	}

	private static void initRuntimePath() {
		runtimePath = getRuntimePath("Cpp");
		runtimeSourcePath = Paths.get(runtimePath, "runtime", "src").toString();
	}

	private static void initCompilerFileName() {
		if (isWindows()) {
			visualStudioPlatformToolset = "v143";
			visualStudioVersion = "2022";
			String[] visualStudioVersions = new String[]{"2022", "2019"};
			String[] visualStudioEditions = new String[]{"BuildTools", "Community", "Professional", "Enterprise"};
			String[] programFilesPaths = new String[]{"Program Files", "Program Files (x86)"};

			for (String version : visualStudioVersions) {
				for (String edition : visualStudioEditions) {
					for (String programFilesPath : programFilesPaths) {
						String file = "C:\\" + programFilesPath + "\\Microsoft Visual Studio\\" + version + "\\"
								+ edition + "\\Msbuild\\Current\\Bin\\MSBuild.exe";
						if (new File(file).exists()) {
							visualStudioPlatformToolset = version.equals("2022") ? "v143" : "v142";
							visualStudioVersion = version;
							compilerPath = file;
							return;
						}
					}
				}
			}
			compilerPath = "MSBuild";
		}
		else {
			compilerPath = "clang++";
		}
	}

	private static void initBinaryPath() {
		if (isWindows()) {
			runtimeBinaryPath = Paths.get(runtimePath, "runtime", "bin", "vs-" + visualStudioVersion, "x64", "Release DLL").toString();
			runtimeLibraryFileName = Paths.get(runtimeBinaryPath, "antlr4-runtime.dll").toString();
		}
		else {
			runtimeBinaryPath = Paths.get(runtimePath, "dist").toString();
			runtimeLibraryFileName = Paths.get(runtimeBinaryPath, "libantlr4-runtime." + (getOS().equals("mac") ? "dylib" : "so")).toString();
		}
	}

	protected String execLexer(String grammarFileName,
	                           String grammarStr,
	                           String lexerName,
	                           String input)
	{
		return execLexer(grammarFileName, grammarStr, lexerName, input, false);
	}

	@Override
	public  String execLexer(String grammarFileName,
	                         String grammarStr,
	                         String lexerName,
	                         String input,
	                         boolean showDFA)
	{
		boolean success = rawGenerateAndBuildRecognizer(grammarFileName,
		                                                grammarStr,
		                                                null,
		                                                lexerName,"-no-listener");
		assertTrue(success);
		writeFile(getTempDirPath(), "input", input);
		writeLexerFile(lexerName, showDFA);
		return execModule("Test.cpp");
	}

	@Override
	public String execParser(String grammarFileName,
	                         String grammarStr,
	                         String parserName,
	                         String lexerName,
	                         String listenerName,
	                         String visitorName,
	                         String startRuleName,
	                         String input,
	                         boolean showDiagnosticErrors)
	{
		boolean success = rawGenerateAndBuildRecognizer(grammarFileName,
		                                                grammarStr,
		                                                parserName,
		                                                lexerName,
		                                                "-visitor");
		assertTrue(success);
		writeFile(getTempDirPath(), "input", input);
		setParseErrors(null);
		writeRecognizerFile(lexerName, parserName, startRuleName, showDiagnosticErrors, false);
		return execRecognizer();
	}

	/** Return true if all is well */
	protected boolean rawGenerateAndBuildRecognizer(String grammarFileName,
	                                                String grammarStr,
	                                                String parserName,
	                                                String lexerName,
	                                                String... extraOptions)
	{
		return rawGenerateAndBuildRecognizer(grammarFileName, grammarStr, parserName, lexerName, false, extraOptions);
	}

	/** Return true if all is well */
	protected boolean rawGenerateAndBuildRecognizer(String grammarFileName,
	                                                String grammarStr,
	                                                String parserName,
	                                                String lexerName,
	                                                boolean defaultListener,
	                                                String... extraOptions)
	{
		ErrorQueue equeue =
			antlrOnString(getTempDirPath(), "Cpp", grammarFileName, grammarStr, defaultListener, extraOptions);
		if (!equeue.errors.isEmpty()) {
			return false;
		}

		List<String> files = new ArrayList<>();
		if ( lexerName!=null ) {
			files.add(lexerName+".cpp");
			files.add(lexerName+".h");
		}
		if ( parserName!=null ) {
			files.add(parserName+".cpp");
			files.add(parserName+".h");
			Set<String> optionsSet = new HashSet<String>(Arrays.asList(extraOptions));
			if (!optionsSet.contains("-no-listener")) {
				files.add(grammarFileName.substring(0, grammarFileName.lastIndexOf('.'))+"Listener.cpp");
				files.add(grammarFileName.substring(0, grammarFileName.lastIndexOf('.'))+"Listener.h");
			}
			if (optionsSet.contains("-visitor")) {
				files.add(grammarFileName.substring(0, grammarFileName.lastIndexOf('.'))+"Visitor.cpp");
				files.add(grammarFileName.substring(0, grammarFileName.lastIndexOf('.'))+"Visitor.h");
			}
		}
		return true; // allIsWell: no compile
	}

	public String execRecognizer() {
		return execModule("Test.cpp");
	}

	public List<String> allCppFiles(String path) {
		ArrayList<String> files = new ArrayList<String>();
		File folder = new File(path);
		File[] listOfFiles = folder.listFiles();
		for (File listOfFile : listOfFiles) {
			String file = listOfFile.getAbsolutePath();
			if (file.endsWith(".cpp")) {
				files.add(file);
			}
		}
		return files;
	}

	public String execModule(String fileName) {
		if (!buildRuntimeIfRequired()) {
			return null;
		}

		if (!buildTestProject()) {
			return null;
		}

		return runTestProject(fileName);
	}

	private boolean buildRuntimeIfRequired() {
		synchronized (BaseCppTest.class) {
			if ( !runtimeBuiltOnce ) {
				try {
					String output = runCommand(new String[] {compilerPath, "--version"},
							getTempDirPath(), "printing compiler version", false);
					System.out.println("Compiler version is: " + output);
				}
				catch (Exception e) {
					System.err.println("Can't get compiler version");
				}

				runtimeBuiltOnce = true;
				if ( !buildRuntime() ) {
					System.out.println("C++ runtime build failed\n");
					return false;
				}
				System.out.println("C++ runtime build succeeded\n");
			}
		}
		return true;
	}

	private boolean buildRuntime() {
		String runtimePath = getRuntimePath();
		System.out.println("Building ANTLR4 C++ runtime (if necessary) at " + runtimePath);

		if (isWindows()) {
			String[] command = {compilerPath, "antlr4cpp-vs" + visualStudioVersion + ".vcxproj", "/p:configuration=Release DLL", "/p:platform=x64" };
			try {
				runCommand(command, runtimePath + "\\runtime", "build ANTLR runtime using MSBuild", false);
			} catch (Exception e) {
				System.err.println("can't build antlr cpp runtime using MSBuild");
			}
		}
		else {
			try {
				String[] command = {"cmake", ".", "-DCMAKE_BUILD_TYPE=Release"};
				runCommand(command, runtimePath, "antlr runtime cmake", false);
			} catch (Exception e) {
				System.err.println("can't configure antlr cpp runtime cmake file");
			}

			try {
				String[] command = {"make", "-j", Integer.toString(Runtime.getRuntime().availableProcessors())};
				runCommand(command, runtimePath, "building antlr runtime", true);
			} catch (Exception e) {
				System.err.println("can't compile antlr cpp runtime");
				e.printStackTrace(System.err);
				try {
					String[] command = {"ls", "-la"};
					String output = runCommand(command, runtimeBinaryPath, "printing library folder content", true);
					System.out.println(output);
				} catch (Exception e2) {
					System.err.println("can't even list folder content");
					e2.printStackTrace(System.err);
				}
			}
		}

		return true;
	}

	private boolean buildTestProject() {
		if (!isWindows()) {
			try {
				String[] linkCommand = new String[]{"ln", "-s", runtimeLibraryFileName};
				runCommand(linkCommand, getTempDirPath(), "sym linking C++ runtime", true);
			} catch (Exception e) {
				System.err.println("can't create link to " + runtimeLibraryFileName);
				e.printStackTrace(System.err);
				return false;
			}
		}

		try {
			List<String> buildCommand = new ArrayList<>();
			buildCommand.add(compilerPath);
			if (isWindows()) {
				buildCommand.add("Test.vcxproj");
				buildCommand.add("/p:configuration=Release");
				buildCommand.add("/p:platform=x64");
			}
			else {
				buildCommand.add("-std=c++17");
				buildCommand.add("-I");
				buildCommand.add(runtimeSourcePath);
				buildCommand.add("-L.");
				buildCommand.add("-lantlr4-runtime");
				buildCommand.add("-pthread");
				buildCommand.add("-o");
				buildCommand.add("Test.out");
				buildCommand.addAll(allCppFiles(getTempDirPath()));
			}
			runCommand(buildCommand.toArray(new String[0]), getTempDirPath(), "building test binary", true);
		} catch (Exception e) {
			System.err.println("can't compile test module: " + e.getMessage());
			e.printStackTrace(System.err);
			return false;
		}

		return true;
	}

	private String runTestProject(String fileName) {
		// Now run the newly minted binary. Reset the error output, as we could have got compiler warnings which are not relevant here.
		setParseErrors(null);
		try {
			String inputPath = new File(getTempTestDir(), "input").getAbsolutePath();
			String exePath = new File(getTempTestDir(), "Test." + (isWindows() ? "exe" : "out")).getAbsolutePath();
			ProcessBuilder builder = new ProcessBuilder(exePath, inputPath);
			builder.directory(getTempTestDir());

			Map<String, String> env = builder.environment();
			if (isWindows()) {
				env.put("PATH", runtimeBinaryPath);
			} else {
				env.put("LD_PRELOAD", runtimeLibraryFileName);
			}

			String output = runProcess(builder, "running test binary", false);
			if ( output.length()==0 ) {
				output = null;
			}

			return output;
		}
		catch (Exception e) {
			System.err.println("can't exec module: " + fileName);
			e.printStackTrace(System.err);
		}
		return null;
	}

	private String runCommand(String[] command, String workPath, String description, boolean showStderr) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(new File(workPath));
		return runProcess(builder, description, showStderr);
	}

	private String runProcess(ProcessBuilder builder, String description, boolean showStderr) throws Exception {
		Process process = builder.start();
		StreamVacuum stdoutVacuum = new StreamVacuum(process.getInputStream());
		StreamVacuum stderrVacuum = new StreamVacuum(process.getErrorStream());
		stdoutVacuum.start();
		stderrVacuum.start();
		int errcode = process.waitFor();
		stdoutVacuum.join();
		stderrVacuum.join();
		String output = stdoutVacuum.toString();
		if ( stderrVacuum.toString().length()>0 ) {
			setParseErrors(stderrVacuum.toString());
			if ( showStderr ) System.err.println(getParseErrors());
		}
		if (errcode != 0) {
			String err = "execution of '"+description+"' failed with error code: "+errcode;
			if ( getParseErrors()!=null ) {
				setParseErrors(getParseErrors() + err);
			}
			else {
				setParseErrors(err);
			}
		}

		return output;
	}

	@Override
	protected void writeRecognizerFile(String lexerName, String parserName, String parserStartRuleName,
									   boolean debug, boolean profile, boolean showDFA,
									   boolean useListener, boolean useVisitor) {
		if (isWindows()) {
			writeVisualStudioProjectFile(lexerName, parserName, useListener, useVisitor);
		}
		super.writeRecognizerFile(lexerName, parserName, parserStartRuleName, debug, profile, showDFA, useListener, useVisitor);
	}

	private void writeVisualStudioProjectFile(String lexerName, String parserName, boolean useListener, boolean useVisitor) {
		ST projectFileST = new ST(visualStudioProjectContent);
		projectFileST.add("platformToolset", visualStudioPlatformToolset);
		projectFileST.add("runtimeSourcePath", runtimeSourcePath);
		projectFileST.add("runtimeBinaryPath", runtimeBinaryPath);
		projectFileST.add("lexerName", lexerName);
		if (parserName != null) {
			projectFileST.add("parserName", parserName);
			String grammarName = parserName.endsWith("Parser")
					? parserName.substring(0, parserName.length() - "Parser".length())
					: parserName;
			projectFileST.add("grammarName", grammarName);
			projectFileST.add("useListener", useListener);
			projectFileST.add("useVisitor", useVisitor);
		}
		writeFile(getTempDirPath(), "Test.vcxproj", projectFileST.render());
	}
}

