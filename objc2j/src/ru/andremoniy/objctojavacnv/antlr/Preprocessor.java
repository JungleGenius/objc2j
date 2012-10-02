package ru.andremoniy.objctojavacnv.antlr;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.andremoniy.objctojavacnv.Context;
import ru.andremoniy.objctojavacnv.Converter;
import ru.andremoniy.objctojavacnv.antlr.output.PreprocessorLexer;
import ru.andremoniy.objctojavacnv.antlr.output.PreprocessorParser;
import ru.andremoniy.objctojavacnv.tokenize.StringToken;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Andremoniy
 * Date: 06.07.12
 * Time: 9:46
 */
public class Preprocessor {

    private static final Logger log = LoggerFactory.getLogger(Preprocessor.class);

    // TODO: properties
    public static final String FRAMEWORKS = "C:\\torrent\\ready\\MacOSX10.6.sdk\\MacOSX10.6.sdk\\System\\Library\\Frameworks\\";
    public static final String VERSIONS_PATH = File.separator + "Versions" + File.separator;
    public static final String HEADERS_PATH1 = ".framework";
    public static final String HEADERS_PATH2 = File.separator + "Headers" + File.separator;

    public boolean preprocessFile(Context context, String fileName, List<String> processedImports, boolean onlyIfs, String rootPath) throws IOException, RecognitionException {
        File mfile = new File(fileName);

        Map<String, String> imports = context.imports;
        Map<String, List<Macros>> defineList = context.macrosMap;

        if (rootPath != null && imports != null && mfile.getName().endsWith(".m")) {
            String importPath = mfile.getPath().substring(rootPath.length(), mfile.getPath().length() - 2).replace(File.separator, ".");
            imports.put(mfile.getName().substring(0, mfile.getName().length() - 2), importPath);
        }

        // ����������� ������ ��������� ��� ������� �����
        if (mfile.getName().endsWith(".h") && mfile.getName().contains("+")) {
            String baseName = mfile.getName().substring(0, mfile.getName().indexOf("+"));
            Set<String> categoriesList = context.categories.get(baseName);
            if (categoriesList == null) {
                categoriesList = new HashSet<>();
                context.categories.put(baseName, categoriesList);
            }
            categoriesList.add(mfile.getPath());
        }

        StringBuilder objCcode = new StringBuilder();
        readObjCode(mfile, objCcode);

        String input = objCcode.toString();

        try {
            preprocessInternal(defineList, fileName, processedImports, onlyIfs, input);
        } catch (Exception e) {
            log.info("Failed to preprocess file: " + fileName);
            log.error(e.getMessage(), e);
            //throw new RuntimeException(e);
        }

        return false;
    }

    private void preprocessInternal(Map<String, List<Macros>> macrosMap, String fileName, List<String> processedImports, boolean onlyIfs, String input) throws RecognitionException, IOException {
        boolean parseManyTimes;
        Map<String, List<Macros>> localMap = new HashMap<>();

        parseManyTimes:
        do {
            if (input.trim().isEmpty()) {
                macrosMap.putAll(localMap);
                return;
            }

            parseManyTimes = false;
            PreprocessorParser.code_return result = getResult(input);

            // ��� ���������� ��� �������� ���� ����� ��������� �������������� (if-� � ������)
            childIterator:
            for (Object child : ((CommonTree) result.getTree()).getChildren()) {
                CommonTree childTree = (CommonTree) child;
                if (!onlyIfs) {
                    switch (childTree.token.getType()) {
                        case PreprocessorParser.T_DEFINE:
                            Macros macros = Macros.build(childTree, fileName);
                            List<Macros> macrosList = macrosMap.get(macros.getName());
                            if (macrosList == null) {
                                macrosList = new ArrayList<>();
                                macrosMap.put(macros.getName(), macrosList);
                            }
                            if (!macrosList.contains(macros)) {
                                macrosList.add(macros);
                            }
                            break;
                        case PreprocessorParser.T_IMPORT:
                            localMap.putAll(loadFromFile(processedImports, childTree));
                            break;
                        case PreprocessorParser.T_INCLUDE:
                            break;
                    }
                } else {
                    switch (childTree.token.getType()) {
                        case PreprocessorParser.T_IF_DEFINE:
                            input = processCommonIfDefine(macrosMap, fileName, processedImports, input, childTree, true);
                            parseManyTimes = true;
                            break childIterator;
                        case PreprocessorParser.T_IF_NOT_DEFINE:
                            input = processCommonIfDefine(macrosMap, fileName, processedImports, input, childTree, false);
                            parseManyTimes = true;
                            break childIterator;
                        case PreprocessorParser.T_IF_SIMPLE:
                            input = processSimpleIfs(input, childTree, macrosMap);
                            writeObjCode(new File(fileName + "p"), input);
                            preprocessInternal(macrosMap, fileName, processedImports, false, input);
                            parseManyTimes = true;
                            break childIterator;
                    }
                }
            }
        } while (parseManyTimes);
        macrosMap.putAll(localMap);
    }

    public static PreprocessorParser.code_return getResult(String input) throws RecognitionException {
        CharStream cs = new ANTLRStringStream(input);
        PreprocessorLexer lexer = new PreprocessorLexer(cs);
        CommonTokenStream tokens = new CommonTokenStream();
        tokens.setTokenSource(lexer);
        PreprocessorParser parser = new PreprocessorParser(tokens);

        return parser.code();
    }

    private String processCommonIfDefine(Map<String, List<Macros>> macrosMap, String fileName, List<String> processedImports, String input, CommonTree childTree, boolean defineDirection) throws RecognitionException, IOException {
        input = processDefineIfs(input, childTree, macrosMap, defineDirection);
        writeObjCode(new File(fileName + "p"), input);
        preprocessInternal(macrosMap, fileName, processedImports, false, input);
        return input;
    }

    private void writeObjCode(File file, String input) {
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            bos.write(input.getBytes("utf-8"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String processDefineIfs(String input, CommonTree tree, Map<String, List<Macros>> macrosMap, boolean defineDirection) {
        boolean isDefined = defineDirection ? testIfDefined(tree, macrosMap) : !testIfDefined(tree, macrosMap);

        return commonIfInternal(input, tree, isDefined);
    }

    private String processSimpleIfs(String input, CommonTree tree, Map<String, List<Macros>> macrosList) {
        CommonTree expressionTree = (CommonTree) tree.getFirstChildWithType(PreprocessorParser.T_EXPRESSION);
        boolean expression = processExpr(expressionTree, macrosList);
        return commonIfInternal(input, tree, expression);
    }

    private boolean processExpr(CommonTree expressionTree, Map<String, List<Macros>> macrosList) {
        boolean expression = true;
        for (Object child : expressionTree.getChildren()) {
            CommonTree childTree = (CommonTree) child;
            switch (childTree.token.getType()) {
                case PreprocessorParser.T_EX_AND:
                    expression = expression && processExAnd(childTree, macrosList);
                    break;
            }
        }
        return expression;
    }

    private boolean processExAnd(CommonTree expressionTree, Map<String, List<Macros>> macrosMap) {
        boolean expression = false;
        for (Object child : expressionTree.getChildren()) {
            CommonTree childTree = (CommonTree) child;
            switch (childTree.token.getType()) {
                case PreprocessorParser.T_NAME:
                    List<Macros> macrosList = macrosMap.get(childTree.getChild(0).getText());
                    Macros macros = null;
                    if (macrosList != null) {
                        for (Macros m : macrosList) {
                            if (m.getParams().isEmpty()) {
                                macros = m;
                                break;
                            }
                        }
                    }
                    expression = expression || macros != null && Integer.parseInt(macros.getReplace().trim()) != 0;
                    break;
                case PreprocessorParser.T_EXPRESSION:
                    expression = expression || processExpr(childTree, macrosMap);
                    break;
                default:
                    if (childTree.token.getText().equals("!")) {
                        expression = !expression;
                    } else {
                        try {
                            Integer intValue = Integer.parseInt(childTree.token.getText());
                            if (intValue != 0) {
                                expression = expression || true;
                            }
                        } catch (Exception ignore) {
                        }
                    }
                    break;
            }
        }
        return expression;
    }

    private String commonIfInternal(String input, CommonTree tree, boolean positive) {
        for (Object child : tree.getChildren()) {
            CommonTree childTree = (CommonTree) child;
            if (positive) {
                switch (childTree.token.getType()) {
                    case PreprocessorParser.T_IF_MAIN:
                        StringBuilder block = getBlock((CommonTree) childTree.getChild(0));
                        input = replaceIfBlock(input, tree, block);
                        return input;
                }
            } else {
                switch (childTree.token.getType()) {
                    case PreprocessorParser.T_IF_ELSE:
                        StringBuilder block = getBlock((CommonTree) childTree.getChild(0));
                        input = replaceIfBlock(input, tree, block);
                        return input;
                }
            }
        }

        input = replaceIfBlock(input, tree, new StringBuilder());
        return input;
    }

    private String replaceIfBlock(String input, CommonTree tree, StringBuilder block) {
        int startLine = tree.getLine();
        int endLine = tree.getFirstChildWithType(PreprocessorLexer.ENDIF).getLine();

        String[] lines = input.split("\r|\n");
        StringBuilder newInput = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i < startLine - 1 || i >= endLine) {
                newInput.append(lines[i]).append("\n");
            } else {
                if (i == startLine) {
                    newInput.append(block);
                }
            }
        }
        return newInput.toString();
    }

    private StringBuilder getBlock(CommonTree tree) {
        StringBuilder block = new StringBuilder();
        for (Object child : tree.getChildren()) {
            CommonTree childTree = (CommonTree) child;
            if (childTree.getChildCount() > 0) {
                block.append(getBlock(childTree));
            } else {
                block.append(((CommonTree) child).getText());
            }
/*
            switch (childTree.token.getType()) {
                case PreprocessorParser.T_BLOCK:
                    block.append(getBlock(childTree));
                    break;
                default:
                    block.append(((CommonTree) child).getText());
                    break;
            }
*/
        }
        return block;
    }

    private boolean testIfDefined(CommonTree tree, Map<String, List<Macros>> macrosMap) {
        for (Object child : tree.getChildren()) {
            CommonTree childTree = (CommonTree) child;
            switch (childTree.token.getType()) {
                case PreprocessorParser.T_NAME:
                    String dName = childTree.getChild(0).getText();
                    return macrosMap.get(dName) != null;
            }
        }
        return false;
    }

    private void readObjCode(File mfile, StringBuilder objCcode) throws IOException {
        try (FileInputStream fis = new FileInputStream(mfile);
             InputStreamReader isr = new InputStreamReader(fis, Charset.forName("utf-8"));
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                objCcode.append(line).append("\n");
            }
        }
    }

    private Map<String, List<Macros>> loadFromFile(List<String> processedImports, CommonTree childTree) throws IOException, RecognitionException {
        String hpath = getBlock(childTree).toString();

        if (processedImports.contains(hpath)) return new HashMap<>();
        processedImports.add(hpath);

        hpath = hpath.substring(hpath.indexOf("<") + 1, hpath.lastIndexOf(">")).trim();
        if (!hpath.contains("/")) return new HashMap<>();
        String frameworkName = hpath.substring(0, hpath.indexOf("/"));
        String headerFile = hpath.substring(hpath.indexOf("/") + 1);

        log.info("Importing " + hpath);

        String path = FRAMEWORKS + frameworkName + HEADERS_PATH1;

        String fileName;
        do {
            path += VERSIONS_PATH;
            File hdir = new File(path);
            if (!hdir.exists() || !hdir.isDirectory()) return new HashMap<>();

            for (File dir : hdir.listFiles()) {
                if (dir.isDirectory()) {
                    path += dir.getName();
                    break;
                }
            }
            fileName = path + File.separator + HEADERS_PATH2 + headerFile;
        } while (!new File(fileName).exists());

        Context localContext = new Context();
        preprocessFile(localContext, fileName, processedImports, Converter.NOT_IFS, null);
        preprocessFile(localContext, fileName, processedImports, Converter.ONLY_IFS, null);
        return localContext.macrosMap;
    }

    public static String replace(String input, Context ctx, String fileName) {
        for (List<Macros> macrosList : ctx.macrosMap.values()) {
            for (Macros macros : macrosList) {
                if (macros.getFileName() != null && macros.getFileName().equals(fileName)) {
                    // �������� ���������� ������� �� ����
                    input = input.replaceAll("\\#define\\s+" + macros.getName(), "//define");
                    if (macros.getReplace().contains("\n")) {
                        input = input.replace(macros.getReplace().replace("//#%#%", "\\"), "//define\n");
                    }
                }
            }
        }
//        boolean changed;
        //    do {
        //       changed = false;
        ru.andremoniy.objctojavacnv.tokenize.StringTokenizer st = new ru.andremoniy.objctojavacnv.tokenize.StringTokenizer(input);
        StringToken token;
        StringBuilder newInput = new StringBuilder();
        tokens_loop:
        while ((token = st.nextToken()) != null) {
            if (!token.isDelimeter) {
                List<Macros> macrosList = ctx.macrosMap.get(token.value);
                if (macrosList != null) {
                    for (Macros macros : macrosList) {
                        if (macros.getParams().isEmpty()) {
                            newInput.append(macros.getReplace());
                            continue tokens_loop;
                        } else {
                            StringToken prevToken = token;
                            token = st.nextToken();
                            if (token.toString().equals("(")) {
                                String replacement = macros.getReplace();
                                List<String> params = macros.getParams();
                                for (int i = 0, paramsSize = params.size(); i < paramsSize; i++) {
                                    String param = params.get(i);
                                    StringToken paramToken;
                                    // ��������� ��� �� ���������� �� �������
                                    while ((paramToken = st.nextToken()).value.matches("\\s")) {
                                    }

                                    // ����� �������� �������� ����� ��� ������
                                    // ����� ������� ��� �� ��������� ������� ��� ������ (���� ��� ��������� ��������)
                                    // �.�. ��� ����� ���� ��� ��� ������ (����� �������, ������, ��������� � ������)
                                    //List<StringToken> paramTokenList = new ArrayList<>();
                                    // ��������� ���� ��, ��� ������ ��� ��������
                                    StringBuilder paramString = new StringBuilder();
                                    paramString.append(paramToken.value);

                                    // ������� �������� ������
                                    int bracketsCounter = 0;
                                    if (paramToken.value.equals("(")) bracketsCounter++;

                                    do {
                                        paramToken = st.nextToken();

                                        // ��� ������ �������?
                                        if (bracketsCounter == 0) {
                                            // �� ��������� ��������? ������ ������� �������
                                            if (i < paramsSize - 1 && paramToken.value.equals(",")) break;
                                            // ��������� ��������? ������ ������� ����������� ������
                                            if (i == paramsSize - 1 && paramToken.value.equals(")")) break;
                                        }

                                        if (paramToken.value.equals("(")) bracketsCounter++;
                                        if (paramToken.value.equals(")")) bracketsCounter--;

                                        paramString.append(paramToken.value);
                                    } while (true);

                                    if (paramString.toString().trim().equals(param)) continue;
                                    String paramRegexp = "[^A-Za-z_]+(" + param + ")[^A-Za-z\\d_]+";
                                    Pattern paramPattern = Pattern.compile(paramRegexp);
                                    Matcher paramMatcher = paramPattern.matcher(replacement);
                                    while (paramMatcher.find()) {
                                        int paramIndex = replacement.indexOf(param, paramMatcher.start());
                                        if (paramIndex >= 0) {
                                            replacement = replacement.substring(0, paramIndex) + paramString.toString().trim() + replacement.substring(paramIndex + param.length());
                                        }
                                    }
                                }
                                newInput.append(replacement);
                                continue tokens_loop;
                            } else {
                                newInput.append(prevToken.value);
                            }
                        }
                    }
                }
            }
            newInput.append(token.value);
        }
        input = newInput.toString();
        //       } while (changed);

//        Map<String, Macros> macrosList = ctx.macrosMap;
//        for (Macros macros : macrosList.values()) {
//            if (macros.getFileName().equals(fileName)) {
//                // �������� ���������� ������� �� ����
//                input = input.replaceAll("\\#define\\s+" + macros.getName(), "//define");
//                if (macros.getReplace().contains("\n")) {
//                    input = input.replace(macros.getReplace().replace("//#%#%", "\\"), "//define\n");
//                }
//            }
//            if (macros.getParams().isEmpty()) {
///*
//                Pattern splitPattern = Pattern.compile("[^A-Za-z\\d_](" + macros.getName() + ")[^A-Za-z\\d_]");
//                Matcher splitMatcher = splitPattern.matcher(input);
//                String newInput = "";
//                int lastSIndex = 0;
//                while (splitMatcher.find()) {
//                    newInput += input.substring(lastSIndex, splitMatcher.start() + 1);
//                    newInput += macros.getReplace();
//                    lastSIndex = splitMatcher.end() - 1;
//                }
//                if (lastSIndex > 0) {
//                    newInput += input.substring(lastSIndex);
//                    input = newInput;
//                }
//*/
//            } else {
//                if (macros.getPattern() == null) {
//                    String regexp = macros.getName() + "\\(";
//                    if (macros.getParams().size() == 1 && (macros.getReplace().equals(macros.getParams().get(0)) || macros.getReplace().trim().isEmpty())) {
//                        regexp += "((.|\\r|\\n)*)";
//                        macros.setSimpleReplace(true);
//                    } else {
//                        boolean f = true;
//                        for (int i = 0; i < macros.getParams().size(); i++) {
//                            if (!f) {
//                                regexp += ",";
//                            } else {
//                                f = false;
//                            }
//                            regexp += "\\s*([A-Za-z_][A-Za-z_\\d]*)(\\s*\\*)?\\s*";
//                        }
//                    }
//                    regexp += "\\)";
//                    macros.setPattern(Pattern.compile(regexp));
//                }
//
//                // �.�. input ����� ���� �������� �������,
//                // �� ����������� ��������� Java bug, ��������� � ����������� ����������� ��������� ���� (x|y)*
//                // ������� ���� input �� �����:
//                Pattern splitPattern = Pattern.compile("[^A..Za..z\\d_]" + macros.getName() + "\\(");
//                Matcher splitMatcher = splitPattern.matcher(input);
//                String newInput = "";
//                int lastSIndex = 0;
//                while (splitMatcher.find()) {
//                    String inputPart = input.substring(lastSIndex, splitMatcher.start());
//                    lastSIndex = splitMatcher.start();
//                    newInput += processInputPart(macros, inputPart);
//                }
//                newInput += processInputPart(macros, input.substring(lastSIndex, input.length()));
//                input = newInput;
//            }
//        }

        return input;
    }

    private static String processInputPart(Macros macros, String inputPart) {
        if (macros.isSimpleReplace()) {
            if (!inputPart.substring(1).startsWith(macros.getName())) return inputPart;
            // �������� ����������� ������ ������ � ������� ���-�� ������
            // ��� ������ �� ���-�� ���������������� - ���������������
            int breaks = 1;
            StringBuffer newInputPart = new StringBuffer(inputPart.substring(0, 1));
            int i = 1 + macros.getName().length() + 1;
            for (; i < inputPart.length(); i++) {
                if (inputPart.charAt(i) == ')') breaks--;
                if (inputPart.charAt(i) == '(') breaks++;
                if (breaks == 0) break;
                newInputPart.append(inputPart.charAt(i));
            }
            inputPart = newInputPart.toString() + inputPart.substring(i + 1);
        } else {
            Matcher m = macros.getPattern().matcher(inputPart);
            while (m.find()) {
                String replacement = macros.getReplace();
                List<String> params = macros.getParams();
                for (int i = 0, paramsSize = params.size(); i < paramsSize; i++) {
                    String param = params.get(i);
                    // skip if param name equals used in code param name
                    if (m.group(i * 2 + 1).equals(param)) continue;
                    String paramRegexp = "[^A-Za-z_]+(" + param + ")[^A-Za-z\\d_]+";
                    Pattern paramPattern = Pattern.compile(paramRegexp);
                    Matcher paramMatcher = paramPattern.matcher(replacement);
                    while (paramMatcher.find()) {
                        int paramIndex = replacement.indexOf(param, paramMatcher.start());
                        replacement = replacement.substring(0, paramIndex) + m.group(i * 2 + 1) + (m.group(i * 2 + 2) != null ? m.group(i * 2 + 2) : "") + replacement.substring(paramIndex + param.length());
                    }
                }
                inputPart = inputPart.substring(0, m.start()) + replacement + inputPart.substring(m.end());
                // ��������� ����� ������, �.�. ����� ���������
                m = macros.getPattern().matcher(inputPart);
            }
        }
        return inputPart;
    }
}
