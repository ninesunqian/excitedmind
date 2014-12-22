package infinitmindmap;

import com.tinkerpop.blueprints.Vertex;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: wangxuguang
 * Date: 14-3-30
 * Time: 下午2:04
 * To change this template use File | Settings | File Templates.
 */
public class MindCombobox extends JComboBox {

    //FIXME: override BasicComboBoxEditor
    class MindComboBoxEditor implements ComboBoxEditor {
        final protected JTextField m_editor;

        public MindComboBoxEditor() {
            m_editor = new JTextField();
            //TODO m_searchInputer->textchanged, call prompter
        }

        public Component getEditorComponent() {
            return m_editor;
        }

        public Object getItem() {
            return m_editor.getText();
        }

        public void addActionListener(ActionListener l) {
            m_editor.addActionListener(l);
        }

        public void removeActionListener(ActionListener l) {
            m_editor.removeActionListener(l);
        }

        public void selectAll() {
        }

        public void setItem(Object newValue) {
        }
    }

    private class PromptedNode {
        final public Object m_dbId;
        final public String m_text;
        final public Object m_parentDBId;
        final public String m_parentText;

        PromptedNode(Vertex vertex)
        {
            m_dbId = vertex.getId();
            m_text = vertex.getProperty(MindModel.sm_textPropName);

            Vertex parent = m_mindDb.getParent(vertex);
            if (parent == null) {
                m_parentDBId = null;
                m_parentText = null;
            } else {
                m_parentDBId = parent.getId();
                m_parentText = parent.getProperty(MindModel.sm_textPropName);
            }
        }
    }

    @Override
    public Component add(Component comp)
    {
        return super.add(comp);    //To change body of overridden methods use File | Settings | File Templates.
    }

    MindCombobox(MindDB mindDb)
    {
        MindComboBoxEditor editor = new MindComboBoxEditor();
        this.setEditor(editor);

        m_followedEditor = (JTextField)editor.getEditorComponent();
        m_followedEditor.getDocument().addDocumentListener(m_editTextListener);

        m_mindDb = mindDb;

        //setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.setPrototypeDisplayValue("WWW");
        this.setMaximumRowCount(8);
    }


    Logger m_logger = LoggerFactory.getLogger(this.getClass());

    private JTextField m_followedEditor;
    private MindDB m_mindDb;

    private ArrayList<PromptedNode> m_promptedNodes = new ArrayList<PromptedNode>();

    private SwingWorker<Boolean, PromptedNode> m_queryWorker;

    public PromptedNode getPromptedNode(int index)
    {
        return m_promptedNodes.get(index);
    }

    private DocumentListener m_editTextListener = new DocumentListener() {

        private void restartQueryWorker()
        {
            if (m_queryWorker != null) {
                m_queryWorker.cancel(true);
            }

            m_queryWorker = new QueryWorker();
            m_queryWorker.execute();
        }

        @Override
        public void insertUpdate(DocumentEvent documentEvent)
        {
            m_logger.info("insert update");
            restartQueryWorker();
        }

        @Override
        public void removeUpdate(DocumentEvent documentEvent)
        {
            m_logger.info("remove update");
            restartQueryWorker();
        }

        @Override
        public void changedUpdate(DocumentEvent documentEvent)
        {
            //do nothing
        }
    };


    private class QueryWorker extends SwingWorker<Boolean, PromptedNode> {
        @Override
        protected Boolean doInBackground()
        {
            MindCombobox.this.removeAllItems();
            MindCombobox.this.hidePopup();

            String inputed = m_followedEditor.getText();

            m_logger.info("query vertex: " + inputed);

            for (Vertex vertex : m_mindDb.getVertices(MindModel.sm_textPropName, inputed)) {
                if (m_mindDb.isVertexTrashed(vertex)) {
                    continue;
                }

                PromptedNode promptedNode = new PromptedNode(vertex);
                publish(promptedNode);
                m_promptedNodes.add(promptedNode);

                if (isCancelled()) {
                    return false;
                }
            }

            return true;
        }

        @Override
        protected void process(List<PromptedNode> promptedNodes)
        {
            MindCombobox.this.showPopup();
            for (PromptedNode promptedNode : promptedNodes) {
                m_logger.info("get promptedNode " + promptedNode.m_dbId);

                if (promptedNode.m_parentText != null) {
                    MindCombobox.this.addItem(promptedNode.m_parentText + " -> " + promptedNode.m_text);
                } else  {
                    MindCombobox.this.addItem("root: " + promptedNode.m_text);
                }
            }
        }
    };


    private boolean layingOut = false;

    public void doLayout(){
        try{
            layingOut = true;
            super.doLayout();
        }finally{
            layingOut = false;
        }
    }

    public Dimension getSize(){
        Dimension dim = super.getSize();
        if(!layingOut) {
            //dim.width = Math.max(dim.width, 100);
            dim.width = Math.max(dim.width, getPreferredSize().width);
        }
        return dim;
    }
}