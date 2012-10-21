package ru.andremoniy.objctojavacnv.context;

import ru.andremoniy.objctojavacnv.antlr.Macros;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Andremoniy
 * Date: 05.10.12
 * Time: 9:03
 */
public class ProjectContext extends AbstractContext {

    public ClassContext classCtx;

    // #define ...
    public Map<String, List<Macros>> macrosMap = new HashMap<String, List<Macros>>();

    // ��� ����� ����� ���������
    public Map<String, String> imports = new HashMap<>();

    // ������ enum-�� ������� header-�
    public Map<String, List<String>> headerEnums = new HashMap<>();

        // ������ ����� ��������� ��� ������� ������
    public Map<String, Set<String>> categories = new HashMap<>();

    public Map<String, Map<String, MethodInterface>> methodsInterfaces = new HashMap<>();

    public Map<String, String> staticFields = new HashMap<>();

    public int m_counter;

    public ClassContext newClass(String className, String categoryName) {
        Map<String, MethodInterface> classMethodsInterfaces = new HashMap<>();
        methodsInterfaces.put(className, classMethodsInterfaces);
        classCtx = new ClassContext(className, categoryName, this, classMethodsInterfaces);
        classCtx.variables.putAll(variables);
        return classCtx;
    }

    public ExpressionContext newExpr() {
        return classCtx.methodCtx.blockCtx.newExpr();
    }
}
