package moe.xing.databindingformatter.utils;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;

import org.jetbrains.annotations.NotNull;

public class FieldUtils {

    //for fork project: PLS change this name
    private static String USING_ANDROIDX = "DATA_BINDING_MODEL_FORMATTER_HOSHI_USING_ANDROIDX";

    /**
     * field has getter or not
     *
     * @return {@code true} field has getter
     * {@code false} otherwise
     */
    public static boolean hasGetter(@NotNull PsiField psiField) {
        return PropertyUtil.findGetterForField(psiField) != null;
    }

    /**
     * field has setter or not
     *
     * @return {@code true} field has setter
     * {@code false} otherwise
     */
    public static boolean hasSetter(@NotNull PsiField psiField) {
        return PropertyUtil.findSetterForField(psiField) != null;
    }

    /**
     * field has data binding getter or not
     *
     * @return {@code true} field has data binding getter
     * {@code false} field does not have getter or getter is Java Only
     */
    public static boolean hasDBGetter(@NotNull PsiField psiField) {
        PsiMethod getter = PropertyUtil.findGetterForField(psiField);
        return getter != null &&
                (getter.getModifierList().findAnnotation("android.databinding.Bindable") != null ||
                        getter.getModifierList().findAnnotation("androidx.databinding.Bindable") != null);
    }

    /**
     * field has data binding setter or not
     *
     * @return {@code true} field has data binding setter
     * {@code false} field does not have setter or setter is Java Only
     * ** only support this plugin format ,PR welcome **
     */
    public static boolean hasDBSetter(@NotNull PsiField psiField) {
        PsiMethod setter = PropertyUtil.findSetterForField(psiField);
        if (setter == null) {
            return false;
        }

        PsiCodeBlock codeBlock = setter.getBody();
        if (codeBlock == null) {
            return false;
        }
        for (PsiStatement psiStatement : codeBlock.getStatements()) {
            if (psiStatement.getText().toLowerCase().contains("notifyChange".toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * field is PropertyChangeRegistry or not
     *
     * @return {@code true} field is PropertyChangeRegistry
     * {@code false} otherwise
     */
    public static boolean isPropertyChangeRegistry(@NotNull PsiField psiField) {

        return psiField.getType().equals(
                PsiType.getTypeByName("android.databinding.PropertyChangeRegistry",
                        psiField.getProject(), GlobalSearchScope.allScope(psiField.getProject())))
                || psiField.getType().equals(
                PsiType.getTypeByName("androidx.databinding.PropertyChangeRegistry",
                        psiField.getProject(), GlobalSearchScope.allScope(psiField.getProject())));


    }

    public static boolean usingAndroidX(Project project) {
        return PropertiesComponent.getInstance(project).getBoolean(USING_ANDROIDX, false);
    }

    public static void setUsingAndroidx(Project project, boolean using) {
        PropertiesComponent.getInstance(project).setValue(USING_ANDROIDX, using);
    }
}
