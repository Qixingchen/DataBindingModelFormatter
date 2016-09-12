package moe.xing.databindingformatter;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.apache.http.util.TextUtils;

/**
 * Created by Qixingchen on 16-9-12.
 *
 * change class and write it.
 */
class WriterUtil extends WriteCommandAction.Simple {

    private PsiClass mClass;
    private PsiElementFactory mFactory;
    private Project mProject;
    private PsiFile mFile;

    WriterUtil(PsiFile mFile, Project project, PsiClass mClass) {
        super(project, mFile);
        mFactory = JavaPsiFacade.getElementFactory(project);
        this.mFile = mFile;
        this.mProject = project;
        this.mClass = mClass;
    }

    @Override
    protected void run() throws Throwable {
        addMethod();
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        CodeStyleManager.getInstance(mProject).reformat(mClass);
    }

    private void addMethod() {

        PsiField[] fields = mClass.getFields();
        mFactory = JavaPsiFacade.getElementFactory(mProject);


        for (PsiField field : fields) {

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
                    "        notifyChange( BR." + field.getName() + ");\n" +
                    "    }";
            mClass.add(mFactory.createMethodFromText(setter, mClass));
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
}
