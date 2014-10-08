package mindworld;

import java.awt.dnd.DragSource;
import java.awt.event.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

import mindworld.operator.*;
import prefuse.Display;
import prefuse.Visualization;

import prefuse.controls.*;
import prefuse.data.*;
import prefuse.util.PrefuseLib;
import prefuse.visual.EdgeItem;
import prefuse.visual.NodeItem;
import prefuse.visual.VisualTree;
import prefuse.visual.sort.TreeDepthItemSorter;

import statemap.State;

/**
 * Demonstration of a node-link tree viewer
 * 
 * @version 1.0
 * @author <a href="http://jheer.org">jeffrey heer</a>
 */
public class MindView extends Display {

    final Logger m_logger = LoggerFactory.getLogger(this.getClass());
    final String m_treeGroupName = "tree";

    final public MindModel m_mindModel;
    MindController m_mindController;

    TreeCursor m_cursor;
    Node m_savedCursor = null;

    TreeFolder m_folder;

    Tree m_tree;
	MindTreeRenderEngine m_renderEngine;

    private MindViewFSM m_fsm;

    private MindPrompter m_prompter;

    private PropertyChangeListener m_fsmStateChangeListener =  new PropertyChangeListener ()
    {
        public void propertyChange(PropertyChangeEvent event) {
            String propertyName = event.getPropertyName();
            State previousState = (State) event.getOldValue();
            State newState = (State) event.getNewValue();

            m_logger.info( "FSM: " + "  event: " + propertyName + ": " + "[" + previousState  + " -> " + newState + "]");
        }
    };

    MouseListener m_prompterMouseListener = new MouseAdapter() {
        public void mouseClicked(MouseEvent mouseEvent) {
            m_fsm.ok(true);
        }
    };

    KeyListener m_editorKeyListener = new KeyAdapter() {

        @Override
        public void keyPressed(KeyEvent e)
        {
            m_newOperator = null;

            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Inserting) {
                    m_fsm.ok(false);
                } else {
                    m_fsm.ok();
                }

                if (m_newOperator != null) {
                    m_mindController.does(m_newOperator);
                    m_newOperator = null;
                }

            }
            else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)  {
                m_fsm.cancel();
            }
        }
    };

    VisualTree m_visualTree;

	public MindView(MindModel mindModel, MindController undoManager, Object rootId) {
		super(new Visualization());

        //s_logger.setLevel(Level.OFF);
		setSize(700, 600);
		setHighQuality(true);

        m_mindModel = mindModel;
        m_mindController = undoManager;

        m_tree = mindModel.findOrPutTree(rootId, 1);
        m_visualTree = (VisualTree)m_vis.add(m_treeGroupName, m_tree);
        MindModel.addNodeMirrorXYColumn(m_tree, m_visualTree);


        m_cursor = new TreeCursor(m_visualTree);
        m_folder = new TreeFolder(m_visualTree);

        setItemSorter(new TreeDepthItemSorter());
        m_renderEngine = new MindTreeRenderEngine(this, m_treeGroupName);

        getTextEditor().addKeyListener(m_editorKeyListener);
		setMouseControlListener();
		setKeyControlListener();

        m_fsm = new MindViewFSM(this, MindViewFSM.MindViewStateMap.Normal);
        m_fsm.addStateChangeListener(m_fsmStateChangeListener);
        m_fsm.enterStartState();

        m_prompter = new MindPrompter(this, m_mindModel.m_mindDb);
        m_prompter.addMouseListener(m_prompterMouseListener);
	}

    public NodeItem toVisual (Node node)
    {
        if (node instanceof NodeItem) {
            return  (NodeItem) node;
        } else {
            String treeNodesGroupName = PrefuseLib.getGroupName(m_treeGroupName, Graph.NODES);
            if (node == null) {
                int i=0;
            }
            return (NodeItem) m_vis.getVisualItem(treeNodesGroupName, node);
        }
    }

    public EdgeItem toVisual (Edge edge)
    {
        if (edge instanceof EdgeItem) {
            return (EdgeItem) edge;
        } else {
            String treeEdgesGroupName = PrefuseLib.getGroupName(m_treeGroupName, Graph.EDGES);
            return (EdgeItem) m_vis.getVisualItem(treeEdgesGroupName, edge);
        }
    }

    public Node toSource (NodeItem nodeItem)
    {
        return (Node) m_vis.getSourceTuple (nodeItem);
    }

    public Edge toSource (EdgeItem edgeItem)
    {
        return (Edge) m_vis.getSourceTuple (edgeItem);
    }

	public void renderTree() {
        /*
        //if current cursor node is deleted by other view, set cursor to root
        if (!m_cursor.m_currentCursor.isValid()) {
            m_cursor.setCursorNodeItem(toVisual(m_tree.getRoot()));
        }
        */
		m_renderEngine.run(null);
	}

    public void renderTree(Runnable runAfterRePaint) {
        m_renderEngine.run(runAfterRePaint);
    }

    ControlAdapter m_zoomToFitContol;
    ControlAdapter m_zoomControl;
    ControlAdapter m_wheelZoomControl;
    ControlAdapter m_panControl;
    MouseControl m_mouseControl;

    private Object[] getPossibleEdgeSource(Node droppedNode, NodeDndControl.HitPosition hitPosition)
    {
        Object ret[] = new Object[2];

        if (droppedNode == m_tree.getRoot()) {
            ret[0] = droppedNode;
            ret[1] = MindModel.getDBChildCount(droppedNode);
            return ret;
        }

        switch (hitPosition) {
            case TOP:
                ret[0] = droppedNode.getParent();
                ret[1] = droppedNode.getIndex();
                break;
            case BOTTOM:
                ret[0] = droppedNode.getParent();
                ret[1] = droppedNode.getIndex() + 1;
                break;
            case RIGHT:
                ret[0] = droppedNode;
                ret[1] = MindModel.getDBChildCount(droppedNode);
                break;
            default:
                ret[0] = null;
                ret[1] = -1;
                break;
        }

        return ret;
    }


    class MouseControl extends NodeDndControl {

        MouseControl() {
            super(MindView.this);
        }

        @Override
        public void nodeItemEntered(NodeItem item, MouseEvent e) {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
                startCursorTimer(item);
            }
        }

        @Override
        public void nodeItemExited(NodeItem item, MouseEvent e) {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
                stopCursorTimer();
                renderTree();
            }
        }

        @Override
        public void nodeItemPressed(NodeItem item, MouseEvent e) {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
                stopCursorTimer();
                m_cursor.setCursorNodeItem(item);
                renderTree();
            }
        }

        @Override
        public void nodeItemClicked(NodeItem item, MouseEvent e) {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
                //if ( !UILib.isButtonPressed(e, Control.MIDDLE_MOUSE_BUTTON))_{
                    toggleFoldNode(getCursorSourceNode());
                //}
                renderTree();
            }
        }

        @Override
        public void dragStart(NodeItem item, DragAction dragAction) {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
                m_fsm.itemDragged(item);
                setCursor(dragAction==DragAction.LINK ? DragSource.DefaultLinkDrop : DragSource.DefaultMoveDrop);
            }
        }

        private boolean canDrop(NodeItem fromNodeItem, NodeItem hitNodeItem, HitPosition hitPosition, DragAction dragAction)
        {
            Node fromNode = toSource(fromNodeItem);
            Node hitNode = toSource(hitNodeItem);
            Object possibleEdgeSource[] = getPossibleEdgeSource(hitNode, hitPosition);

            Node parentNode = fromNode.getParent();
            Node newParentNode = (Node)possibleEdgeSource[0];

            if (possibleEdgeSource[0] == null) {
                return false;
            }

            switch (dragAction) {
                case LINK:
                    return true;
                case MOVE:
                    if (!MindModel.getDBId(parentNode).equals(MindModel.getDBId(newParentNode))) {
                        if (! m_mindModel.isRefNode(fromNode)) {
                            return m_mindModel.canResetParent(fromNode, (Node)possibleEdgeSource[0]);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }

        private void setCursorShape(NodeItem sourceNode, NodeItem hitNode, HitPosition hitPosition, DragAction dragAction)
        {
            boolean cursorEnabled = (hitNode == null) || canDrop(sourceNode, hitNode, hitPosition, dragAction);

            switch (dragAction) {
                case LINK:
                    setCursor(cursorEnabled ? DragSource.DefaultLinkDrop : DragSource.DefaultLinkNoDrop);
                    break;
                case MOVE:
                    setCursor(cursorEnabled ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop);
                    break;
                default:
                    break;
            }
        }

        //if dropNode or dropPostion changed, give the event
        @Override
        public void dragHit(NodeItem item, NodeItem hitNode,
                            NodeDndControl.HitPosition hitPosition, DragAction dragAction)
        {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Dragging) {
                setCursorShape(item, hitNode, hitPosition, dragAction);
                renderTree();
            }
        }

        @Override
        public void dragMiss(NodeItem item, NodeItem dropNode, DragAction dragAction)
        {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Dragging) {
                setCursorShape(item, null, HitPosition.OUTSIDE, dragAction);
                renderTree();
            }
        }

        @Override
        public void dragEnd(NodeItem draggedNode, NodeItem droppedNode, HitPosition hitPosition, DragAction dragAction)
        {
            m_newOperator = null;

            m_logger.info("nodeItemDropped");
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Dragging) {

                if (droppedNode != null) {
                    m_logger.info(String.format("--- dragAction %s", dragAction.toString()));
                    if (canDrop(draggedNode, droppedNode, hitPosition, dragAction)) {
                        m_fsm.itemDropped(draggedNode, droppedNode, hitPosition, dragAction);
                    } else {
                        m_fsm.cancel();
                    }
                } else {
                    m_fsm.cancel();
                }
            }

            if (m_newOperator != null) {
                m_mindController.does(m_newOperator);
                m_newOperator = null;
            }
        }

        @Override
        public void dragActionChanged(NodeItem draggedNode, NodeItem hitNode, HitPosition hitPosition, DragAction dragAction)
        {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Dragging) {
                setCursorShape(draggedNode, hitNode, hitPosition, dragAction);
            }
        }
    };

    public NodeItem getDragHitNode()
    {
        return m_mouseControl.m_hitNode;
    }

	private void setMouseControlListener()
    {
		m_zoomToFitContol = new ZoomToFitControl(Control.MIDDLE_MOUSE_BUTTON);
		m_zoomControl = new ZoomControl();
		m_wheelZoomControl = new WheelZoomControl();
		m_panControl = new PanControl();

		m_mouseControl = new MouseControl();

        addControlListener(m_zoomToFitContol);
        addControlListener(m_zoomControl);
        addControlListener(m_wheelZoomControl);
        addControlListener(m_panControl);
        addControlListener(m_mouseControl);
	}

    void setMouseControlEnabled(boolean enabled)
    {
        m_zoomToFitContol.setEnabled(enabled);
        m_zoomControl.setEnabled(enabled);
        m_wheelZoomControl.setEnabled(enabled);
        m_panControl.setEnabled(enabled);
        m_mouseControl.setEnabled(enabled);
    }

    void startEditing()
    {
        showEditor(false);
    }

    void stopEditing(boolean confirm)
    {
        if (confirm) {
            String text = getTextEditor().getText();
            SettingProperty settingProperty = new SettingProperty(m_mindModel,
                    getCursorSourceNode(), MindModel.sm_textPropName, text);

            settingProperty.does();
            m_mindController.does(settingProperty);
        }

        hideEditor();
    }

    private void showEditor(boolean withPrompter)
    {
        if (getTextEditor().isVisible())
            return;

        editText(toVisual(getCursorSourceNode()), MindModel.sm_textPropName) ;

        if (withPrompter) {
            m_prompter.show(getTextEditor());
        }
    }

    private void hideEditor()
    {
        super.stopEditing2(false);
        m_prompter.hide();
    }

    public void setCursorNodeByPath(ArrayList<Integer> path)
    {
        m_cursor.setCursorNodeItem(toVisual(m_mindModel.getNodeByPath(m_tree, path)));
    }

    void startInserting(boolean asChild)
    {
        Node cursorNode = getCursorSourceNode();
        if (asChild) {
            if (isFolded(cursorNode)) {
                unfoldNode(cursorNode);
            }
        } else {
            if (cursorNode == m_tree.getRoot()) {
                assert(false);
                return;
            }
        }

        addPlaceholder(asChild);

        renderTree(new Runnable() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        showEditor(true);
                    }
                });
            }
        });
    }

    void stopInserting(boolean confirm, boolean fromPrompter)
    {
        if (confirm) {
            MindOperator operator;

            if (fromPrompter) {
                int selectedIndex = m_prompter.getSelectedIndex();
                MindPrompter.PromptedNode selected = m_prompter.getPromptedNode(selectedIndex);

                operator = new AddingReference(m_mindModel, getCursorSourceNode().getParent(),
                        selected.m_dbId, getCursorSourceNode().getIndex());
            } else {
                String text = getTextEditor().getText();
                operator = new AddingChild(m_mindModel, getCursorSourceNode().getParent(), getCursorSourceNode().getIndex(), text);
            }

            removePlaceholder();
            m_mindController.does(operator);

        } else {
            removePlaceholder();
        }

        hideEditor();
    }

    void importFile()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String path;

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            path = chooser.getSelectedFile().getPath();
        } else {
            return;
        }

        MindOperator operator = new ImportingFile(m_mindModel, getCursorSourceNode(), path);

        m_mindController.does(operator);
    }

    NormalStateAction m_importAction = new NormalStateAction()  {

        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            importFile();
        }
    };

    abstract class NormalStateAction extends AbstractAction {
        final public void actionPerformed(ActionEvent e) {
            if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
                stopCursorTimer();
                NormalStateActionPerformed(e);
                assert(m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal);
                //if NormalStatActionPermaled call m_mindControl.does(operator), will renderTree()
                renderTree();
            }
        }

        //this memthod must not change state
        abstract public void NormalStateActionPerformed(ActionEvent e);
    }

    private Timer m_cursorTimer;

    void startCursorTimer(final NodeItem nodeItem)
    {
        m_cursorTimer = new Timer(500, new NormalStateAction() {

            public void NormalStateActionPerformed(ActionEvent e) {
                if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
                    m_cursor.setCursorNodeItem(nodeItem);
                    stopCursorTimer();
                    renderTree();
                }
            }
        });
        m_cursorTimer.setRepeats(false);
        m_cursorTimer.setCoalesce(true);
        m_cursorTimer.start();
    }

    void stopCursorTimer()
    {
        if (m_cursorTimer != null) {
            m_cursorTimer.stop();
            m_cursorTimer = null;
        }
    }


    NormalStateAction m_cursorLeftAction = new NormalStateAction() {
        public void NormalStateActionPerformed(ActionEvent e) {
            m_cursor.moveLeft();
        }
    };

    NormalStateAction m_cursorRightAction = new NormalStateAction() {
        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            m_cursor.moveRight();
        }
    };

    NormalStateAction m_cursorUpAction = new NormalStateAction() {
        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            m_cursor.moveUp();
        }
    };

    NormalStateAction m_cursorDownAction = new NormalStateAction() {
        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            m_cursor.moveDown();
        }
    };

    NormalStateAction m_undoAction = new NormalStateAction() {
        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            if (m_mindController.canUndo()) {
                m_mindController.undo();
            }
        }
    };

    NormalStateAction m_redoAction = new NormalStateAction() {
        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            if (m_mindController.canRedo()) {
                m_mindController.redo();
            }
        }
    };

    NormalStateAction m_saveAction = new NormalStateAction() {
        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            m_mindModel.m_mindDb.commit();
        }
    };

    boolean canRemove()
    {
        return Removing.canDo(m_mindModel, m_tree, getCursorSourceNode());
    }

    public void removeCursor()
    {
        Node cursorNode = getCursorSourceNode();
        MindOperator operator = new Removing(m_mindModel, cursorNode);
        m_mindController.does(operator);
    }

    NormalStateAction m_removeAction = new NormalStateAction()  {

        @Override
        public void NormalStateActionPerformed(ActionEvent e) {
            if (canRemove())
            {
                removeCursor();
            }
        }
    };

    public AbstractAction m_addChildAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            m_fsm.startInserting(true);
        }
    };

    public AbstractAction m_editAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            m_fsm.startEditing();
        }
    };

    private void alert(String msg)
    {
        JOptionPane.showMessageDialog(null, msg);
    }

    boolean canStartInserting(boolean asChild)
    {
        if (asChild) {
            return true;

        } else {
            if (getCursorSourceNode() == m_tree.getRoot()) {
                alert("you must open the root parent");
                return false;
            } else {
                return true;
            }
        }
    }

    public AbstractAction m_addSiblingAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            m_fsm.startInserting(false);
        }
    };

    final static String sm_editActionName = "edit";
    final static String sm_undoActionName = "undo";
    final static String sm_redoActionName = "redo";
    final static String sm_saveActionName = "save";

    final static String sm_addChildActionName = "addChild";
    final static String sm_addSiblingActionName = "addSibling";
    final static String sm_removeActionName = "remove";



    final static String sm_cursorLeft = "cursorLeft";
    final static String sm_cursorRight = "cursorRight";
    final static String sm_cursorUp = "cursorUp";
    final static String sm_cursorDown = "cursorDown";

    public void setKeyControlListener() {
        /*
        ActionMap m_mindActionMap = new ActionMap();
        ActionMap defaultActionMap = getActionMap();
        m_mindActionMap.setParent(defaultActionMap);
        setActionMap(m_mindActionMap);
        */
        ActionMap m_mindActionMap = getActionMap();

        m_mindActionMap.put(sm_editActionName, m_editAction);
        m_mindActionMap.put(sm_removeActionName, m_removeAction);
        m_mindActionMap.put(sm_addChildActionName, m_addChildAction);
        m_mindActionMap.put(sm_addSiblingActionName, m_addSiblingAction);

        m_mindActionMap.put(sm_undoActionName, m_undoAction);
        m_mindActionMap.put(sm_redoActionName, m_redoAction);
        m_mindActionMap.put(sm_saveActionName, m_saveAction);

        m_mindActionMap.put(sm_cursorLeft, m_cursorLeftAction);
        m_mindActionMap.put(sm_cursorRight, m_cursorRightAction);
        m_mindActionMap.put(sm_cursorUp, m_cursorUpAction);
        m_mindActionMap.put(sm_cursorDown, m_cursorDownAction);

        InputMap inputMap = getInputMap();
        inputMap.put(KeyStroke.getKeyStroke("F2"), sm_editActionName);
        inputMap.put(KeyStroke.getKeyStroke("DELETE"), sm_removeActionName);
        inputMap.put(KeyStroke.getKeyStroke("INSERT"), sm_addChildActionName);
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), sm_addSiblingActionName);

        inputMap.put(KeyStroke.getKeyStroke("ctrl Z"), sm_undoActionName);
        inputMap.put(KeyStroke.getKeyStroke("ctrl Y"), sm_redoActionName);
        inputMap.put(KeyStroke.getKeyStroke("ctrl S"), sm_saveActionName);

        inputMap.put(KeyStroke.getKeyStroke("LEFT"), sm_cursorLeft);
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), sm_cursorRight);
        inputMap.put(KeyStroke.getKeyStroke("UP"), sm_cursorUp);
        inputMap.put(KeyStroke.getKeyStroke("DOWN"), sm_cursorDown);
    }

    private void addPlaceholder(boolean asChild)
    {
        Node cursorNode = getCursorSourceNode();
        m_savedCursor = cursorNode;

        Node newNode;

        if (asChild) {

            newNode = m_tree.addChild(cursorNode, cursorNode.getChildCount());
            m_folder.unfoldNode(toVisual(cursorNode));

        } else {
            newNode = m_tree.addChild(cursorNode.getParent(), cursorNode.getIndex() + 1);
        }

        //NOTE: newNode.setString(MindModel.sm_textPropName, "") error

        newNode.set(MindModel.sm_textPropName, "");

        m_cursor.setCursorNodeItem(toVisual(newNode));
    }

    private void removePlaceholder()
    {
        Node placeholderNode = getCursorSourceNode();
        assert(isPlaceholer(placeholderNode));
        assert(placeholderNode != m_tree.getRoot());

        m_tree.removeChild(placeholderNode);
        m_cursor.setCursorNodeItem(toVisual(m_savedCursor));
    }

    //include node and edge, the edge is used rendering
    public boolean isPlaceholer(Tuple tuple)
    {
        return (m_mindModel.getDBId(tuple) == null);
    }

    public Node getCursorSourceNode()
    {
        return toSource(m_cursor.getCursorNodeItem());
    }

    private boolean isFolded(Node node)
    {
        if (node.getChildCount() > 0) {
            NodeItem item = toVisual(node);
            return ! item.isExpanded();
        } else {
            return m_mindModel.getDBChildCount(node) > 0;
        }
    }

    private void unfoldNode(Node node)
    {
        if (node.getChildCount() == 0) { // node is not a leaf node
            m_mindModel.attachChildren(node);
        }
        m_folder.unfoldNode(toVisual(node));
    }

    private void foldNode(Node node)
    {
        m_folder.foldNode(toVisual(node));
    }

    public void toggleFoldNode(Node node)
    {
        if (isFolded(node)) {
            unfoldNode(node);
        }
        else {
            foldNode(node);
        }
    }

    public void dragAndDropNode(Node draggedNode, Node droppedNode,
                                   NodeDndControl.HitPosition hitPosition,
                                   NodeDndControl.DragAction dragAction)
    {
        Object possibleEdgeSource[] = getPossibleEdgeSource(droppedNode, hitPosition);
        m_newOperator = null;

        if (dragAction == NodeDndControl.DragAction.LINK) {
            Node referrer = (Node)possibleEdgeSource[0];
            int position = (Integer)possibleEdgeSource[1];

            m_newOperator = new AddingReference(m_mindModel, draggedNode, referrer, position);
        } else {
            Node newParent = (Node)possibleEdgeSource[0];
            int newPosition = (Integer)possibleEdgeSource[1];

            assert(m_mindModel.canResetParent(draggedNode, newParent));

            if (draggedNode == m_tree.getRoot()) {
                m_logger.info("forbid drag prefuse root to other as child");
                return;
            }

            Node parent = draggedNode.getParent();

            if (MindModel.getDBId(newParent).equals(MindModel.getDBId(parent))) {
                int oldPosition = draggedNode.getIndex();
                if (oldPosition < newPosition) {
                    newPosition--;
                }

                m_newOperator = new ChangingPosition(m_mindModel, draggedNode, newPosition);

            } else {
                if (m_mindModel.isRefNode(draggedNode)) {
                    m_newOperator = new HandoveringReference(m_mindModel, draggedNode, newParent, newPosition);
                } else {

                    MindDB.InheritDirection inheritDirection = m_mindModel.getInheritDirection(draggedNode, newParent);
                    assert inheritDirection != MindDB.InheritDirection.LINEAL_DESCENDANT;

                    m_newOperator = new HandoveringChild(m_mindModel, draggedNode, newParent, newPosition);
                }
            }
        }
    }

    public void setCursorProperty(String key, Object value)
    {
        if (m_fsm.getState() == MindViewFSM.MindViewStateMap.Normal) {
            //called by toolbar controls' action listener,
            stopCursorTimer();

            Node cursorNode = getCursorSourceNode();
            MindOperator operator = new SettingProperty(m_mindModel, cursorNode, key, value);

            m_mindController.does(operator);
        }
    }

    MindOperator m_newOperator;

} // end of class TreeMap
