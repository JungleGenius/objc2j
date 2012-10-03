package ru.andremoniy.objctojavacnv;

/**
 * User: Andremoniy
 * Date: 26.08.12
 * Time: 20:28
 */

/**
 * ��������, ������� �������� ��� ������ ��������� ��� �������� �������
 */
public class CurrentContext {

    Context ctx;
    String className;
    boolean staticFlag;
    String methodName;
    public boolean isBreak;
    public boolean skipBreak;
    public boolean transformClassNames;
    public boolean skipObjField;
    public boolean addReturnNull;

    public CurrentContext(CurrentContext current, boolean staticFlag, String methodName) {
        this(current, staticFlag, false, false);
        this.methodName = methodName;
    }

    public CurrentContext(Context ctx, String className, boolean staticFlag) {
        this.ctx = ctx;
        this.className = className;
        this.staticFlag = staticFlag;
    }

    public CurrentContext(CurrentContext current, boolean staticFlag, boolean skipObjField, boolean transformClassNames) {
        this.ctx = current.ctx;
        this.className = current.className;
        this.staticFlag = staticFlag;
        this.skipObjField = skipObjField;
        this.transformClassNames = transformClassNames;
    }

    public CurrentContext gem() {
        return this.gem(staticFlag);
    }

    public CurrentContext gem(boolean staticFlag) {
        return new CurrentContext(this, staticFlag, skipObjField, transformClassNames);
    }

    public CurrentContext gem(boolean static_flag, String methodName) {
        return new CurrentContext(this, static_flag, methodName);
    }
}
