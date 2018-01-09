package moe.xing.databindingformatter;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;

import org.apache.http.util.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

import moe.xing.databindingformatter.utils.FieldUtils;

/**
 * Created by Qixingchen on 16-9-12.
 * <p>
 * change class and write it.
 */
class WriterUtil extends WriteCommandAction.Simple {

    private PsiClass mClass;
    private PsiElementFactory mFactory;
    private Project mProject;
    private PsiFile mFile;
    private List<PsiField> mFields;
    private GlobalSearchScope mSearchScope;
    private static Logger LOGGER = Logger.getLogger(WriterUtil.class.getName());

    WriterUtil(PsiFile mFile, Project project, PsiClass mClass, List<PsiField> fields) {
        super(project, mFile);
        mFactory = JavaPsiFacade.getElementFactory(project);
        this.mFile = mFile;
        this.mProject = project;
        this.mClass = mClass;
        this.mFields = fields;
        this.mSearchScope = GlobalSearchScope.allScope(getProject());
    }

    @Override
    protected void run() throws Throwable {
        mFactory = JavaPsiFacade.getElementFactory(mProject);
        addDataBinding();
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        CodeStyleManager.getInstance(mProject).reformat(mClass);
    }

    private void addDataBinding() {

        addImplements();
        addGetterAndSetter(mFields);
        addPropertyMethods();
    }

    /**
     * add implements Observable
     */
    private void addImplements() {
        final PsiClassType[] implementsListTypes = mClass.getImplementsListTypes();
        PsiClass Observable = JavaPsiFacade.getInstance(getProject()).findClass("android.databinding.Observable", mSearchScope);
        if (Observable == null) {
            return;
        }

        // If already implemented,return
        for (PsiClassType implementsListType : implementsListTypes) {
            PsiClass resolve = implementsListType.resolve();
            if (resolve != null && Observable.getQualifiedName() != null && Observable.getQualifiedName().equals(resolve.getQualifiedName())) {
                return;
            }
        }


        PsiJavaCodeReferenceElement codeReferenceElement = mFactory.createClassReferenceElement(Observable);
        PsiReferenceList implementsList = mClass.getImplementsList();
        if (implementsList != null) {
            implementsList.add(codeReferenceElement);
        }

    }

    /**
     * add getter and setter for fields
     *
     * @param fields filed need add getter / setter
     */
    private void addGetterAndSetter(List<PsiField> fields) {
        String BRName = findBR();

        for (PsiField field : fields) {
            if (!FieldUtils.hasDBGetter(field)) {
                if (FieldUtils.hasGetter(field)) {
                    addDBForJavaGetter(field);
                } else {
                    addGetter(field);
                }
            }
            if (!FieldUtils.hasDBSetter(field)) {
                if (FieldUtils.hasSetter(field)) {
                    addDBForJavaSetter(field, BRName);
                } else {
                    addSetter(field, BRName);
                }
            }
        }
    }

    /**
     * add DB getter for Field which does not have getter
     *
     * @param field field need add DB getter
     */
    private void addGetter(@NotNull PsiField field) {
        String getter =
                "public " + field.getType().getPresentableText() + " get" + getFirstUpCaseName(field.getName()) +
                        "(){ \n" +
                        "return " + field.getName() + "; \n" +
                        "}";
        PsiMethod getMethod = mFactory.createMethodFromText(getter, mClass);
        getMethod.getModifierList().addAnnotation("android.databinding.Bindable");
        mClass.add(getMethod);
    }

    /**
     * add DB part for a java getter
     *
     * @param psiField field which has a getter need add DB part
     */
    private void addDBForJavaGetter(@NotNull PsiField psiField) {
        PsiMethod getter = PropertyUtil.findGetterForField(psiField);
        assert getter != null;
        getter.getModifierList().addAnnotation("android.databinding.Bindable");
    }

    /**
     * add DB setter for Field which does not have setter
     *
     * @param field field need add DB setter
     */
    private void addSetter(@NotNull PsiField field, @NotNull String BRName) {
        String setter = "public void set" + getFirstUpCaseName(field.getName()) +
                "(" + field.getType().getPresentableText() + " " +
                field.getName() + "){\n " +
                "        this." + field.getName() + " = " + field.getName() + ";\n" +
                "        notifyChange( " + BRName + "." + field.getName() + ");\n" +
                "    }";
        mClass.add(mFactory.createMethodFromText(setter, mClass));
    }

    /**
     * add DB part for a java setter
     *
     * @param psiField field which has a setter need add DB part
     */
    private void addDBForJavaSetter(@NotNull PsiField psiField, @NotNull String BRName) {
        PsiMethod setter = PropertyUtil.findSetterForField(psiField);
        PsiCodeBlock codeBlock = setter.getBody();
        if (codeBlock == null) {
            return;
        }
        PsiStatement last = codeBlock.getStatements()[codeBlock.getStatements().length - 1];
        PsiStatement notify = mFactory.createStatementFromText("notifyChange( " + BRName + "." + psiField.getName() + ");", setter);
        codeBlock.addAfter(notify, last);
    }

    /**
     * add PropertyChangeRegistry field / notifyChange / addCallback / removeCallback
     */
    private void addPropertyMethods() {
        boolean fieldExist = false;
        boolean notifyChangeExist = false;
        boolean addListenerExist = false;
        boolean removeListenerExist = false;
        for (PsiField field : mClass.getFields()) {
            // if has PropertyChangeRegistry ,do not add
            if (field.getType().equals(PsiType.getTypeByName("android.databinding.PropertyChangeRegistry", getProject(), mSearchScope))) {
                fieldExist = true;
                break;
            }
        }

        for (PsiMethod psiMethod : mClass.getMethods()) {
            if ("notifyChange".toLowerCase().equals(psiMethod.getName().toLowerCase())) {
                notifyChangeExist = true;
            }
            if ("addOnPropertyChangedCallback".toLowerCase().equals(psiMethod.getName().toLowerCase())) {
                addListenerExist = true;
            }
            if ("removeOnPropertyChangedCallback".toLowerCase().equals(psiMethod.getName().toLowerCase())) {
                removeListenerExist = true;
            }
        }

        if (!fieldExist) {
            String pcrFieldCreate = "private "
                    .concat(" transient android.databinding.PropertyChangeRegistry propertyChangeRegistry = new android.databinding.PropertyChangeRegistry();");

            mClass.add(mFactory.createFieldFromText(pcrFieldCreate, mClass));
        }

        if (!notifyChangeExist) {

            String pcrNotifyMethodCreate = "private synchronized void notifyChange(int propertyId) {\n" +
                    "        if (propertyChangeRegistry == null) {\n" +
                    "            propertyChangeRegistry = new PropertyChangeRegistry();\n" +
                    "        }\n" +
                    "        propertyChangeRegistry.notifyChange(this, propertyId);\n" +
                    "    } ";

            mClass.add(mFactory.createMethodFromText(pcrNotifyMethodCreate, mClass));
        }
        if (!addListenerExist) {
            String pcrAddListener =
                    "public synchronized void addOnPropertyChangedCallback(OnPropertyChangedCallback callback) {\n" +
                            "        if (propertyChangeRegistry == null) {\n" +
                            "            propertyChangeRegistry = new PropertyChangeRegistry();\n" +
                            "        }\n" +
                            "        propertyChangeRegistry.add(callback);\n" +
                            "\n" +
                            "    }";
            PsiMethod pcrAddListenerMethod = mFactory.createMethodFromText(pcrAddListener, mClass);
            pcrAddListenerMethod.getModifierList().addAnnotation("Override");
            mClass.add(pcrAddListenerMethod);
        }
        if (!removeListenerExist) {
            String pcrRemoveListener =
                    "public synchronized void removeOnPropertyChangedCallback(OnPropertyChangedCallback callback) {\n" +
                            "        if (propertyChangeRegistry != null) {\n" +
                            "            propertyChangeRegistry.remove(callback);\n" +
                            "        }\n" +
                            "    }";
            PsiMethod pcrRemoveListenerMethod = mFactory.createMethodFromText(pcrRemoveListener, mClass);
            pcrRemoveListenerMethod.getModifierList().addAnnotation("Override");
            mClass.add(pcrRemoveListenerMethod);
        }
    }

    private String getFirstUpCaseName(String name) {
        if (TextUtils.isEmpty(name)) {
            return name;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * find BR class name
     * find start with class package,to parent package
     * if not find ,return "BR"
     *
     * @return BR class name
     */
    @NotNull
    private String findBR() {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(getProject());
        String packageName = ((PsiJavaFile) mClass.getContainingFile()).getPackageName();
        while (packageName.contains(".")) {
            PsiClass BRClass = JavaPsiFacade.getInstance(getProject()).findClass(packageName + ".BR", scope);
            if (BRClass != null) {
                String name = BRClass.getQualifiedName();
                if (name != null && name.startsWith(".")) {
                    name = name.replaceFirst(".", "");
                }
                return name == null ? "BR" : name;
            }
            packageName = packageName.substring(0, packageName.lastIndexOf("."));
        }
        return "BR";
    }
}
