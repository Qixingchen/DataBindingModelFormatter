package moe.xing.databindingformatter;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

import org.apache.http.util.TextUtils;

import javax.annotation.Nullable;

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
    private GlobalSearchScope mSearchScope;

    WriterUtil(PsiFile mFile, Project project, PsiClass mClass) {
        super(project, mFile);
        mFactory = JavaPsiFacade.getElementFactory(project);
        this.mFile = mFile;
        this.mProject = project;
        this.mClass = mClass;
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
        addGetterAndSetter();
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
     * add getter and setter for field
     */
    private void addGetterAndSetter() {
        String BRName = findBR();
        PsiField[] fields = mClass.getFields();

        for (PsiField field : fields) {
            // if is PropertyChangeRegistry ,continue
            if (field.getType().equals(PsiType.getTypeByName("android.databinding.PropertyChangeRegistry", getProject(), mSearchScope))) {
                continue;
            }

            String getter =
                    "public " + field.getType().getPresentableText() + " get" + getFirstUpCaseName(field.getName()) +
                            "(){ \n" +
                            "return " + field.getName() + "; \n" +
                            "}";
            PsiMethod getMethod = mFactory.createMethodFromText(getter, mClass);
            getMethod.getModifierList().addAnnotation("android.databinding.Bindable");
            mClass.add(getMethod);

            String setter = "public void set" + getFirstUpCaseName(field.getName()) +
                    "(" + field.getType().getPresentableText() + " " +
                    field.getName() + "){\n " +
                    "        this." + field.getName() + " = " + field.getName() + ";\n" +
                    "        notifyChange( " + BRName + "." + field.getName() + ");\n" +
                    "    }";
            mClass.add(mFactory.createMethodFromText(setter, mClass));
        }
    }

    /**
     * add PropertyChangeRegistry field and addCallback & removeCallback
     */
    private void addPropertyMethods() {
        for (PsiField field : mClass.getFields()) {
            // if has PropertyChangeRegistry ,do not add
            // TODO: 2017/8/4 find add/remove listener method
            if (field.getType().equals(PsiType.getTypeByName("android.databinding.PropertyChangeRegistry", getProject(), mSearchScope))) {
                return;
            }
        }

        String pcrFieldCreate = "private "
                .concat(" transient android.databinding.PropertyChangeRegistry propertyChangeRegistry = new android.databinding.PropertyChangeRegistry();");

        mClass.add(mFactory.createFieldFromText(pcrFieldCreate, mClass));

        String pcrNotifyMethodCreate = "private void notifyChange(int propertyId) {\n" +
                "        if (propertyChangeRegistry == null) {\n" +
                "            propertyChangeRegistry = new PropertyChangeRegistry();\n" +
                "        }\n" +
                "        propertyChangeRegistry.notifyChange(this, propertyId);\n" +
                "    } ";

        mClass.add(mFactory.createMethodFromText(pcrNotifyMethodCreate, mClass));

        String pcrAddListener =
                "public void addOnPropertyChangedCallback(OnPropertyChangedCallback callback) {\n" +
                        "        if (propertyChangeRegistry == null) {\n" +
                        "            propertyChangeRegistry = new PropertyChangeRegistry();\n" +
                        "        }\n" +
                        "        propertyChangeRegistry.add(callback);\n" +
                        "\n" +
                        "    }";
        PsiMethod pcrAddListenerMethod = mFactory.createMethodFromText(pcrAddListener, mClass);
        pcrAddListenerMethod.getModifierList().addAnnotation("Override");
        mClass.add(pcrAddListenerMethod);

        String pcrRemoveListener =
                "public void removeOnPropertyChangedCallback(OnPropertyChangedCallback callback) {\n" +
                        "        if (propertyChangeRegistry != null) {\n" +
                        "            propertyChangeRegistry.remove(callback);\n" +
                        "        }\n" +
                        "    }";
        PsiMethod pcrRemoveListenerMethod = mFactory.createMethodFromText(pcrRemoveListener, mClass);
        pcrRemoveListenerMethod.getModifierList().addAnnotation("Override");
        mClass.add(pcrRemoveListenerMethod);
    }

    private String getFirstUpCaseName(String name) {
        if (TextUtils.isEmpty(name)) {
            return name;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @Nullable
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
                return name;
            }
            packageName = packageName.substring(0, packageName.lastIndexOf("."));
        }
        return "BR";
    }
}
