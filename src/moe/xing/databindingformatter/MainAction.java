package moe.xing.databindingformatter;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * Created by qixingchen on 16-9-7.
 *
 * MainAction
 */
public class MainAction extends BaseGenerateAction {


    @SuppressWarnings("unused")
    public MainAction() {
        super(null);
    }

    @SuppressWarnings("unused")
    public MainAction(CodeInsightActionHandler handler) {
        super(handler);
    }

    @Override
    protected boolean isValidForClass(final PsiClass targetClass) {
        return super.isValidForClass(targetClass);
    }

    @Override
    public boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {

        return super.isValidForFile(project, editor, file);
    }

    public void actionPerformed(AnActionEvent event) {

        PsiClass mClass;

        Project mProject;

        mProject = event.getData(PlatformDataKeys.PROJECT);
        Editor editor = event.getData(PlatformDataKeys.EDITOR);

        assert editor != null;
        assert mProject != null;
        PsiFile mFile = PsiUtilBase.getPsiFileInEditor(editor, mProject);

        assert mFile != null;
        mClass = getTargetClass(editor, mFile);

        try {
            new WriterUtil(mFile, mProject, mClass).execute();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }

}
