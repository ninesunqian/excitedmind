package infinitmindmap;

import infinitmindmap.operator.Removing;
import prefuse.data.Node;
import prefuse.data.Table;
import prefuse.data.Tree;
import prefuse.util.collections.IntIterator;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: Administrator
 * Date: 14-3-2
 * Time: 下午9:57
 * To change this template use File | Settings | File Templates.
 */
public class MindController extends UndoManager {
    Logger m_logger = LoggerFactory.getLogger(this.getClass());

    Hashtable<Object, MindView> m_mindViews = new Hashtable<Object, MindView>();

    MindModel m_mindModel;
    JTabbedPane m_tabbedPane;

    ArrayList<NodeControl> m_externalMouseContollers =
            new ArrayList<NodeControl>();

    MindController(MindModel mindModel, JTabbedPane tabbedPane) {
        super();
        m_mindModel = mindModel;
        m_tabbedPane = tabbedPane;

        m_tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                Component comp = m_tabbedPane.getSelectedComponent();
                comp.requestFocusInWindow();
            }
        });
        findOrAddMindView(m_mindModel.m_mindDb.getRootId());

        //防止切换tab时，焦点被切换到工具栏
        m_tabbedPane.setFocusCycleRoot(true);
    }

    void addExternalMouseController(NodeControl robustNodeItemController)
    {
        if (m_externalMouseContollers.contains(robustNodeItemController)) {
            return;
        }

        for (Object rootDBId : m_mindViews.keySet()) {
            m_mindViews.get(rootDBId).addControlListener(robustNodeItemController);
        }
    }

    public MindView findOrAddMindView(Object rootDBId) {
        MindView mindView = m_mindViews.get(rootDBId);
        if (mindView != null) {
            return mindView;
        }

        mindView = new MindKeyView(m_mindModel, this, rootDBId);

        m_mindViews.put(rootDBId, mindView);
        Node root = mindView.m_tree.getRoot();

        m_tabbedPane.addTab(m_mindModel.getText(root), mindView);

        for(NodeControl controller : m_externalMouseContollers) {
            mindView.addControlListener(controller);
        }

        int lastMindViewIndex = m_tabbedPane.getTabCount() - 1;
        if (lastMindViewIndex < 9) {
            m_tabbedPane.setMnemonicAt(lastMindViewIndex, KeyEvent.VK_1 + lastMindViewIndex);
        } else if (lastMindViewIndex == 9) {
            m_tabbedPane.setMnemonicAt(lastMindViewIndex, KeyEvent.VK_0);
        } else {
            //not setMnemonicAt
        }

        return  mindView;
    }

    public void removeMindView(Object rootDBId) {
        MindView mindView = m_mindViews.get(rootDBId);
        m_tabbedPane.remove(mindView);
        m_mindViews.remove(rootDBId);
    }

    public MindView exposeMindView(Object rootDBId) {
        MindView mindView = m_mindViews.get(rootDBId);
        if (mindView == null) {
            findOrAddMindView(rootDBId);
        }

        if (m_tabbedPane.getSelectedComponent() != mindView) {
            m_tabbedPane.setSelectedComponent(mindView);
        }
        return mindView;
    }

    public MindView getCurrentView() {
        return (MindView)m_tabbedPane.getSelectedComponent();
    }

    public Object getCurrentVertexId() {
        MindView currentView = getCurrentView();
        if (currentView == null) {
            return null;
        }

        Node node = currentView.getCursorSourceNode();
        return m_mindModel.getDBId(node);
    }

    String getTreeNodeRows(Tree tree)
    {
        Table nodeTable = tree.getNodeTable();

        IntIterator allRows = nodeTable.rows();

        String str = "[";

        while (allRows.hasNext()) {
             str += ((Integer)allRows.nextInt()).toString() + ", ";
        }

        str += "],  ";

        Table edgeTable = tree.getEdgeTable();
        allRows = edgeTable.rows();

        while (allRows.hasNext()) {
            str += ((Integer)allRows.nextInt()).toString() + ", ";
        }
        str += "]";

        return str;
    }

    private void updateMindViews(MindOperator operator, boolean isUndo)
    {
        //remove no needed mindview
        if (operator instanceof Removing) {
            Removing removing = (Removing)operator;
            Object trashedDBId = removing.m_removedDBId;

            for (Object rootDBId : m_mindViews.keySet()) {
                if (m_mindModel.m_mindDb.isVertexIdDescendant(trashedDBId, rootDBId)) {
                    removeMindView(rootDBId);
                }
            }
        }

        if (operator == null) {
            int i = 0;
        }

        MindView operatorBornView = exposeMindView(operator.m_rootDBId);

        //repaint remain mindviews
        for (Object rootDBId : m_mindViews.keySet()) {

            MindView mindView = m_mindViews.get(rootDBId);
            if (mindView == operatorBornView) {
                mindView.setCursorNodeByPath(isUndo ? operator.m_formerCursorPath : operator.m_laterCursorPath);
            } else {
                //not using mindView.getCursorSourceNode(), because if nodeItem is not valid,
                //the source node is can't be got
                if (! mindView.m_cursor.m_currentCursor.isValid()) {
                    ArrayList<Integer> rootPath = new ArrayList<Integer>();
                    mindView.setCursorNodeByPath(rootPath);
                }
            }

            mindView.renderTreeToEndChanging();
        }
    }

    public boolean does(UndoableEdit edit) {
        MindOperator operator = (MindOperator)edit;
        try {
            operator.does();
            updateMindViews(operator, false);

            m_logger.info("m_formerCursorPath: " + operator.m_formerCursorPath.toString());
            m_logger.info("m_laterCursorPath: " + operator.m_laterCursorPath.toString());

            return super.addEdit(edit);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage(), e.getMessage(), JOptionPane.ERROR_MESSAGE);
            m_logger.warn("operator exception" + e.getMessage());
        }

        return true;
    }


    public void redo()
    {
        if (! canRedo())
            return;

        MindOperator operator = (MindOperator)editToBeRedone();
        super.redo();

        updateMindViews(operator, false);
    }

    public void undo()
    {
        if (! canUndo())
            return;

        MindOperator operator = (MindOperator)editToBeUndone();
        super.undo();

        updateMindViews(operator, true);
    }
}