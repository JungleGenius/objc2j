package ru.andremoniy.objctojavacnv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.andremoniy.objctojavacnv.antlr.Macros;
import ru.andremoniy.objctojavacnv.antlr.Preprocessor;
import ru.andremoniy.objctojavacnv.context.ProjectContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: Andremoniy
 * Date: 18.06.12
 * Time: 9:35
 */
public final class Converter {

    public static final Logger log = LoggerFactory.getLogger(Converter.class);

    public static final boolean NOT_IFS = false;
    public static final boolean ONLY_IFS = true;

    private Converter() {
    }

    public static boolean preprocess(ProjectContext projectContext, String rootPath, String path, List<String> processedImports, boolean onlyIfs) {
        File dir = new File(path);
        for (File f : dir.listFiles()) {
            if (!f.isDirectory()) {
                if (f.getName().endsWith(".m") || f.getName().endsWith(".h")) {
                    try {
                        boolean wasIfs = Preprocessor.preprocessFile(projectContext, f.getAbsolutePath(), processedImports, onlyIfs, rootPath);
                        if (wasIfs) return true;
                    } catch (Exception e) {
                        log.info("Failed to preprocess file: " + f.getAbsolutePath());
                        log.error(e.getMessage(), e);
                    }
                }
            } else {
                preprocess(projectContext, rootPath, f.getAbsolutePath(), processedImports, onlyIfs);
                log.info(f.getAbsolutePath() + " preprocessed...");
            }
        }
        return false;
    }

    /**
     * Run converter on given project path
     *
     * @param path    project path
     * @param skipSDK skip SDK // TODO: description
     * @return project context
     * @throws IOException
     */
    public static ProjectContext convert(String path, boolean skipSDK) throws IOException {
        ProjectContext projectContext = new ProjectContext(skipSDK);
        projectContext.load();

        List<String> processedImports = new ArrayList<>();
        do {
            preprocess(projectContext, path, path, processedImports, NOT_IFS);
        } while (preprocess(projectContext, path, path, processedImports, ONLY_IFS));

        // add special macroses:
        // todo: customization
        projectContext.macrosMap.put("DEPRECATED_IN_MAC_OS_X_VERSION_10_0_AND_LATER", Arrays.asList(new Macros("DEPRECATED_IN_MAC_OS_X_VERSION_10_0_AND_LATER", "")));
        projectContext.macrosMap.put("DEPRECATED_IN_MAC_OS_X_VERSION_10_1_AND_LATER", Arrays.asList(new Macros("DEPRECATED_IN_MAC_OS_X_VERSION_10_1_AND_LATER", "")));
        projectContext.macrosMap.put("DEPRECATED_IN_MAC_OS_X_VERSION_10_2_AND_LATER", Arrays.asList(new Macros("DEPRECATED_IN_MAC_OS_X_VERSION_10_2_AND_LATER", "")));
        projectContext.macrosMap.put("DEPRECATED_IN_MAC_OS_X_VERSION_10_3_AND_LATER", Arrays.asList(new Macros("DEPRECATED_IN_MAC_OS_X_VERSION_10_3_AND_LATER", "")));
        projectContext.macrosMap.put("DEPRECATED_IN_MAC_OS_X_VERSION_10_4_AND_LATER", Arrays.asList(new Macros("DEPRECATED_IN_MAC_OS_X_VERSION_10_4_AND_LATER", "")));
        projectContext.macrosMap.put("DEPRECATED_IN_MAC_OS_X_VERSION_10_5_AND_LATER", Arrays.asList(new Macros("DEPRECATED_IN_MAC_OS_X_VERSION_10_5_AND_LATER", "")));
        projectContext.macrosMap.put("DEPRECATED_IN_MAC_OS_X_VERSION_10_6_AND_LATER", Arrays.asList(new Macros("DEPRECATED_IN_MAC_OS_X_VERSION_10_6_AND_LATER", "")));
        projectContext.macrosMap.put("AVAILABLE_MAC_OS_X_VERSION_10_0_AND_LATER", Arrays.asList(new Macros("AVAILABLE_MAC_OS_X_VERSION_10_0_AND_LATER", "")));
        projectContext.macrosMap.put("AVAILABLE_MAC_OS_X_VERSION_10_1_AND_LATER", Arrays.asList(new Macros("AVAILABLE_MAC_OS_X_VERSION_10_1_AND_LATER", "")));
        projectContext.macrosMap.put("AVAILABLE_MAC_OS_X_VERSION_10_2_AND_LATER", Arrays.asList(new Macros("AVAILABLE_MAC_OS_X_VERSION_10_2_AND_LATER", "")));
        projectContext.macrosMap.put("AVAILABLE_MAC_OS_X_VERSION_10_3_AND_LATER", Arrays.asList(new Macros("AVAILABLE_MAC_OS_X_VERSION_10_3_AND_LATER", "")));
        projectContext.macrosMap.put("AVAILABLE_MAC_OS_X_VERSION_10_4_AND_LATER", Arrays.asList(new Macros("AVAILABLE_MAC_OS_X_VERSION_10_4_AND_LATER", "")));
        projectContext.macrosMap.put("AVAILABLE_MAC_OS_X_VERSION_10_5_AND_LATER", Arrays.asList(new Macros("AVAILABLE_MAC_OS_X_VERSION_10_5_AND_LATER", "")));
        projectContext.macrosMap.put("AVAILABLE_MAC_OS_X_VERSION_10_6_AND_LATER", Arrays.asList(new Macros("AVAILABLE_MAC_OS_X_VERSION_10_6_AND_LATER", "")));

        convertInternal(projectContext, path, 0); // ������������ ������� header �����
        for (int i = 0; i < 2; i++) {
            convertInternal(projectContext, path, 1); // ������������ m-�����
        }

        return projectContext;
    }

    private static void convertInternal(ProjectContext projectContext, String path, int whatConvert) {
        File dir = new File(path);
        List<File> files = new ArrayList<>(Arrays.asList(dir.listFiles()));

        // ������� header �����
        if (whatConvert == 0) {
            for (File f : files) {
                if (f.getName().contains("+")) {
                    log.info(f.getAbsolutePath() + " skipped due to '+' in name...");
                    continue;
                }
                if (f.getName().endsWith(".h")) {
                    try {
                        log.info(f.getAbsolutePath() + " converting...");
                        ConverterH.convert_h(f.getAbsolutePath(), projectContext, null, null);
                        log.info(f.getAbsolutePath() + " converted...");
                        log.info(projectContext.h_counter + " headers converted...");
                    } catch (Exception e) {
                        log.info("Error converting " + f.getAbsolutePath());
                        log.error(e.getMessage(), e);
                    }
                }
            }
        } else {
            // ����� m �����
            for (File f : files) {
                if (f.getName().contains("+")) {
                    log.info(f.getAbsolutePath() + " skipped due to '+' in name...");
                    continue;
                }
                if (f.getName().endsWith(".m")) {
                    try {
                        log.info(f.getAbsolutePath() + " converting...");
                        ConverterM.convert_m(f.getAbsolutePath(), projectContext, new StringBuilder());
                        log.info(f.getAbsolutePath() + " converted...");
                    } catch (Exception e) {
                        log.info("Error converting " + f.getAbsolutePath());
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }

        // �� � ��������
        for (File f : files) {
            if (f.isDirectory()) {
                convertInternal(projectContext, f.getAbsolutePath(), whatConvert);
                log.info(f.getAbsolutePath() + " converted...");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java -jar objc2j.jar <project_path>\n-where <project_path> - root directory with your Objective-C project.");
            System.exit(1);
        }
        if (!new File("converter.properties").exists()) {
            System.out.println("You should create 'converter.properties' file and place there path to MacOSX.sdk");
            System.exit(1);
        }
        String projectPath = args[0];
        File convDir = new File(args[0]);
        if (!convDir.exists() || !convDir.isDirectory()) {
            System.out.println("Invalid <project_path> value: " + projectPath);
            System.exit(1);
        }
        ConverterProperties properties = ConverterProperties.PROPERTIES; // just for initialize

        Converter.convert(projectPath, false);
    }

}
