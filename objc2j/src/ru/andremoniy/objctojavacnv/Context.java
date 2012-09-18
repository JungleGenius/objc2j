package ru.andremoniy.objctojavacnv;

import ru.andremoniy.objctojavacnv.antlr.Macros;

import java.util.*;

/**
 * User: Andremoniy
 * Date: 26.08.12
 * Time: 0:48
 */
public class Context {

    public static int h_counter = 0;
    public static int m_counter = 0;

    // #define ...
    public Map<String, List<Macros>> macrosMap = new HashMap<String, List<Macros>>();

    // ��� ����� ����� ���������
    public Map<String, String> imports = new HashMap<>();

    // ������ enum-�� ������� header-�
    public Map<String, List<String>> headerEnums = new HashMap<>();

    public List<String> localProcessedImports = new ArrayList<>();
    public List<String> addImports = new ArrayList<>();

    // �������� ����������� ���������
    public Set<String> localCategories = new HashSet<>();
    public String categoryList;

    // ������ ����� ��������� ��� ������� ������
    public Map<String, Set<String>> categories = new HashMap<>();

    public boolean containsInit;
    public boolean containsAutoRelease;

    public void renew() {
        localProcessedImports.clear();
        addImports.clear();
        localCategories.clear();
        categoryList = "";
        containsAutoRelease = false;
        containsInit = false;
    }
}
