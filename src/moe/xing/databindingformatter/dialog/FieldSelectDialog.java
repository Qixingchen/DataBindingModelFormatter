package moe.xing.databindingformatter.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;

import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import moe.xing.databindingformatter.utils.FieldUtils;

@SuppressWarnings("Convert2Lambda")
public class FieldSelectDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JList fieldList;
    private JCheckBox checkBoxAndroidX;
    private DefaultListModel<Field> listModel = new DefaultListModel<>();

    @NotNull
    private FieldSelectEvent mEvent;

    @NotNull
    private PsiClass mPsiClass;

    @NotNull
    private Project mProject;

    public FieldSelectDialog(@NotNull FieldSelectEvent event, @NotNull PsiClass psiClass, @Nonnull Project project) {
        mEvent = event;
        mPsiClass = psiClass;
        mProject = project;
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        setTitle("Data Binding Formatter");

        fieldList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // using AndroidX
        checkBoxAndroidX.setSelected(FieldUtils.usingAndroidX(mProject));

        checkBoxAndroidX.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                FieldUtils.setUsingAndroidx(mProject, checkBoxAndroidX.isSelected());
            }
        });

        init();
    }

    private void init() {
        for (PsiField psiField : mPsiClass.getFields()) {
            if (FieldUtils.hasDBGetter(psiField) && FieldUtils.hasDBSetter(psiField)) {
                continue;
            }
            if (FieldUtils.isPropertyChangeRegistry(psiField)) {
                continue;
            }
            listModel.addElement(new Field(psiField));
        }
        //noinspection unchecked
        fieldList.setModel(listModel);
        int size = fieldList.getModel().getSize();
        if (size > 0) {
            fieldList.setSelectionInterval(0, size - 1);
        }
    }

    private void onOK() {
        List<PsiField> psiFields = new ArrayList<>();
        for (Object o : fieldList.getSelectedValuesList()) {
            if (o instanceof Field) {
                psiFields.add(((Field) o).mPsiField);
            }
        }
        mEvent.onOK(psiFields);
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public interface FieldSelectEvent {

        /**
         * call on OK click
         *
         * @param fields selected fields
         */
        void onOK(@NotNull List<PsiField> fields);
    }

    class Field {
        @NotNull
        private PsiField mPsiField;

        Field(@NotNull PsiField psiField) {

            mPsiField = psiField;
        }

        @NotNull
        public PsiField getPsiField() {
            return mPsiField;
        }

        public void setPsiField(@NotNull PsiField psiField) {
            mPsiField = psiField;
        }

        @Override
        public String toString() {
            StringBuilder title = new StringBuilder();
            title.append(mPsiField.getName());
            title.append(" ");
            if (FieldUtils.hasGetter(mPsiField)) {
                if (FieldUtils.hasDBGetter(mPsiField)) {
                    title.append("DB Getter Exist ");
                } else {
                    title.append("Java Getter Exist ");
                }
            }

            if (FieldUtils.hasSetter(mPsiField)) {
                if (FieldUtils.hasDBSetter(mPsiField)) {
                    title.append("DB Setter Exist ");
                } else {
                    title.append("Java Setter Exist ");
                }
            }
            return title.toString();
        }
    }
}
