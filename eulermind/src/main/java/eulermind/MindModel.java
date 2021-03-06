package eulermind;

import com.orientechnologies.orient.core.id.ORecordId;
import com.tinkerpop.blueprints.*;
import eulermind.MindDB.EdgeVertex;
import eulermind.MindDB.RefLinkInfo;
import eulermind.component.SwingWorkerDialog;
import eulermind.importer.DirectoryImporter;
import eulermind.importer.FreemindImporter;
import eulermind.importer.Importer;
import eulermind.importer.TikaPlainTextImporter;
import eulermind.view.MindView;
import prefuse.data.*;
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.event.EventConstants;
import prefuse.data.event.TableListener;
import prefuse.util.FontLib;
import prefuse.util.TypeLib;
import prefuse.util.collections.IntIterator;
import prefuse.visual.VisualItem;
import prefuse.visual.VisualTable;
import prefuse.visual.VisualTree;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
The MIT License (MIT)
Copyright (c) 2012-2014 wangxuguang ninesunqian@163.com

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

public class MindModel {
    static Logger s_logger = LoggerFactory.getLogger(MindModel.class);

	final static String sm_dbIdColumnName = "dbElementId";

    private final static String CHILDREN_ATTACHED_PROP_NAME = "childrenAttached";
    private final static String EDGE_INNER_ID_PROP_NAME = MindDB.EDGE_INNER_ID_PROP_NAME;

    //这两个属性应用频繁
    public final static String TEXT_PROP_NAME = "x"; //"t" 已经占用了
    public final static String STYLE_PROP_NAME = "s";

    public final static String sm_iconPropName = "ic";
    public final static String sm_textColorPropName = "tc";
    public final static String sm_nodeColorPropName = "nc";
    public final static String sm_italicPropName = "it";
    public final static String sm_boldPropName = "bd";
    public final static String sm_fontSizePropName = "sz";
    public final static String sm_fontFamilyPropName = "ft";

    private final static String FAVORITE_INDEX_NAME = "favoriteIndex";
    private final static String FAVORITE_KEY_NAME = "favorite";

    private final static String LAST_OPENED_INDEX_NAME = "lastOpenedIndex";
    private final static String LAST_OPENED_KEY_NAME = "lastOpened";

    private Index<Vertex> m_favoriteIndex;

    public class VertexBasicInfo {
        public Object m_dbId;
        public String m_text;

        public Object m_parentDbId;
        public String m_parentText;

        public String m_contextText;

        public VertexBasicInfo(Vertex vertex) {
            m_dbId = vertex.getId();
            m_text = vertex.getProperty(TEXT_PROP_NAME);
            MindDB.EdgeVertex edgeParent = m_mindDb.getParentEge(vertex);

            if (edgeParent == null) {
                m_parentText = null;
                m_parentDbId = null;
            } else {
                Vertex parent = edgeParent.m_source;
                m_parentDbId = parent.getId();
                m_parentText = parent.getProperty(TEXT_PROP_NAME);
            }

            //如果m_parentText在前面，需要考虑m_parentText可能过长，总长度过可能长，m_text可能过短等问题
            m_contextText = m_parentText + " > " + m_text;
        }
    }

    ArrayList<VertexBasicInfo> m_favoriteInfoes = new ArrayList<VertexBasicInfo>();

    public final static String sm_nodePropNames [] = {
            TEXT_PROP_NAME,
            STYLE_PROP_NAME,

            sm_iconPropName,

            sm_fontFamilyPropName,
            sm_fontSizePropName,

            sm_boldPropName,
            sm_italicPropName,

            sm_nodeColorPropName,
            sm_textColorPropName,
    };

    private static Hashtable<String, Class> sm_propertyClassMap = new Hashtable<String, Class>();

	public final static String sm_edgeTypePropName = MindDB.EDGE_TYPE_PROP_NAME;

    public final static String sm_edgePropNames [] = {
    };

	public OrientMindDb m_mindDb;

    //需要记住打开tree的位置，所以不能用HashSet
    private ArrayList<Tree> m_trees = new ArrayList<Tree>();

	//return sorted copy of propName
	private void addNodeTableProperties(Table t)
	{
		t.addColumn(sm_dbIdColumnName, Object.class, null);
        t.addColumn(CHILDREN_ATTACHED_PROP_NAME, boolean.class, false);

		for (String propName : sm_nodePropNames)
		{
			t.addColumn(propName, Object.class, null);
		}
	}

    private void addEdgeTableProperties(Table t)
    {
        t.addColumn(sm_dbIdColumnName, Object.class, null);
        t.addColumn(sm_edgeTypePropName, Object.class, null);
        t.addColumn(EDGE_INNER_ID_PROP_NAME, Object.class, null);

        for (String propName : sm_nodePropNames)
        {
            t.addColumn(propName, Object.class, null);
        }

    }

    private void loadNodeProperties (Vertex vertex, Node node)
    {
        assert(vertex != null && vertex.getId() != null);

        node.set(sm_dbIdColumnName, vertex.getId());

        loadElementProperties(vertex, node, sm_nodePropNames);
    }

    private void loadEdgeProperties (com.tinkerpop.blueprints.Edge dbEdge, Edge edge)
    {
        assert(dbEdge != null && dbEdge.getId() != null);

        edge.set(sm_dbIdColumnName, dbEdge.getId());
        edge.set(sm_edgeTypePropName, dbEdge.getProperty(MindDB.EDGE_TYPE_PROP_NAME));
        edge.set(EDGE_INNER_ID_PROP_NAME, dbEdge.getProperty(MindDB.EDGE_INNER_ID_PROP_NAME));

        loadElementProperties(dbEdge, edge, sm_edgePropNames);

    }

    //store 仅仅保存与图无关的属性
    protected void storeNodeProperties(Vertex vertex, Node node)
    {
        storeElementProperties(vertex, node, sm_nodePropNames);
    }

    private void storeEdgeProperties (com.tinkerpop.blueprints.Edge dbEdge, Edge edge)
    {
        storeElementProperties(dbEdge, edge, sm_edgePropNames);
    }


    private static void fillPropertyClassMap()
    {
        if (sm_propertyClassMap.size() > 0) {
            return;
        }

        sm_propertyClassMap.put(TEXT_PROP_NAME, String.class);
        sm_propertyClassMap.put(STYLE_PROP_NAME, String.class);

        sm_propertyClassMap.put(sm_iconPropName, String.class);

        sm_propertyClassMap.put(sm_fontFamilyPropName, String.class);
        sm_propertyClassMap.put(sm_fontSizePropName, Integer.class);

        sm_propertyClassMap.put(sm_boldPropName, Boolean.class);
        sm_propertyClassMap.put(sm_italicPropName, Boolean.class);

        sm_propertyClassMap.put(sm_nodeColorPropName, Integer.class);
        sm_propertyClassMap.put(sm_textColorPropName, Integer.class);

    }

	public MindModel(String dbPath)
	{
        fillPropertyClassMap();

		m_mindDb = new OrientMindDb(dbPath);
        m_mindDb.startUp();
        m_favoriteIndex = m_mindDb.getOrCreateIndex(FAVORITE_INDEX_NAME);

        Vertex root = m_mindDb.getVertex(m_mindDb.getRootId());

        if (! m_favoriteIndex.get(FAVORITE_KEY_NAME, FAVORITE_KEY_NAME).iterator().hasNext()) {
            root.setProperty(TEXT_PROP_NAME, "root");

            EdgeVertex edgeVertex = m_mindDb.addChild(root, 0);
            edgeVertex.m_target.setProperty(TEXT_PROP_NAME, "child_1");

            edgeVertex = m_mindDb.addChild(root, 1);
            edgeVertex.m_target.setProperty(TEXT_PROP_NAME, "child_2");

            m_mindDb.addRefEdge(root, root, 2);

            m_favoriteIndex.put(FAVORITE_KEY_NAME, FAVORITE_KEY_NAME, root);
        }

        for (Vertex vertex : m_favoriteIndex.get(FAVORITE_KEY_NAME, FAVORITE_KEY_NAME))  {
            m_favoriteInfoes.add(new VertexBasicInfo(vertex));
        }

    }

    ArrayList<Object> getLastOpenedRootId()
    {
        Index<Vertex> lastOpenedIndex = m_mindDb.getOrCreateIndex(LAST_OPENED_INDEX_NAME);

        ArrayList<Object> lastOpenedRootId = new ArrayList<Object>();
        for (int i=0; lastOpenedIndex.get(LAST_OPENED_KEY_NAME, i).iterator().hasNext(); i++) {
            Vertex vertex = lastOpenedIndex.get(LAST_OPENED_KEY_NAME, i).iterator().next();
            lastOpenedRootId.add(vertex.getId());
        }
        return lastOpenedRootId;
    }

    public void saveLastOpenedDbIds() {
        Index<Vertex> lastOpenedIndex = m_mindDb.getOrCreateIndex(LAST_OPENED_INDEX_NAME);

        //FIXME: 此处不能用 dropIndex来代替
        for (int i=0; lastOpenedIndex.get(LAST_OPENED_KEY_NAME, i).iterator().hasNext(); i++) {
            Vertex vertex = lastOpenedIndex.get(LAST_OPENED_KEY_NAME, i).iterator().next();
            lastOpenedIndex.remove(LAST_OPENED_KEY_NAME, i, vertex);
        }

        for (int i=0; i<m_trees.size(); i++) {
            Tree tree = m_trees.get(i);
            Vertex vertex = m_mindDb.getVertex(tree.getRoot().get(sm_dbIdColumnName));
            lastOpenedIndex.put(LAST_OPENED_KEY_NAME, i, vertex);
        }

    }

    public void close()
    {
        m_mindDb.shutdown();
    }

    public Tree findTree(Object rootId)
    {
        for (Tree tree : m_trees) {
            if (tree.getRoot().get(sm_dbIdColumnName).equals(rootId)) {
                return tree;
            }
        }
        return null;
    }

    public prefuse.data.Tree findOrPutTree(Object rootId)
    {
        Tree tree;

        tree = findTree(rootId);

        if (tree != null) {
            return tree;
        }

        tree = new Tree();
        Table displayNodeTable = tree.getNodeTable();
        Table displayEdgeTable = tree.getEdgeTable();

        addNodeTableProperties(displayNodeTable);
        addEdgeTableProperties(displayEdgeTable);

        displayNodeTable.setTupleToStringHandler(new Table.TupleToStringHandler() {
            @Override
            public String tupleToString(Table table, Tuple tuple)
            {
                return getNodeDebugInfo((Node)tuple);
            }
        });

        m_trees.add(tree);

        Node root = tree.addRoot();
        loadNodeProperties(m_mindDb.getVertex(rootId), root);

        final int expandLevel = 2;
        tree.depthFirstTraverse(root, new Tree.DepthFirstTraverseProcessor() {
            public boolean run(Node parent, Node node, int level) {
                attachChildren(node);
                return level < expandLevel;
            }
        });

        updateChildrenAttached();

        return tree;
    }

    public List<Tree> getDisplaySubTrees()
    {
        return Collections.unmodifiableList(m_trees);
    }

    public void closeSubTree(Tree tree)
    {
        m_trees.remove(tree);
    }

	private void loadElementProperties(com.tinkerpop.blueprints.Element dbElement, Tuple tuple, String keys[])
	{
        assert (keys == sm_nodePropNames || keys == sm_edgePropNames);
		for (String key : keys)
		{
            tuple.set(key, dbElement.getProperty(key));
		}
	}

    private void storeElementProperties(com.tinkerpop.blueprints.Element dbElement, Tuple tuple, String keys[])
    {
        assert (keys == sm_nodePropNames || keys == sm_edgePropNames);

        for (String key : keys)
        {
            Object value = tuple.get(key);
            if (value == null) {
                dbElement.removeProperty(key);
            } else {
                dbElement.setProperty(key, value);
            }
        }
    }

    private void verifyElementProperties(com.tinkerpop.blueprints.Element dbElement,
                                                   Tuple tuple, String keys[])
    {
        if (true) {
            return;
        }

        assert dbElement.getId().equals(tuple.get(sm_dbIdColumnName));

        for (String key : keys)
        {
            Object tupleValue = tuple.get(key);

            Object dbElementValue = dbElement.getProperty(key);
            if (!(tupleValue == dbElementValue || tupleValue.equals(dbElementValue))) {
                int debug = 1;
            }
            assert tupleValue == dbElementValue || tupleValue.equals(dbElementValue);
        }
    }
	private Vertex getDBVertex(Node node)
	{
		Object dbId = node.get(sm_dbIdColumnName);
		return m_mindDb.getVertex(dbId);
	}
	
	private com.tinkerpop.blueprints.Edge getDBEdge (Edge edge)
	{
        if (edge == null || edge.get(sm_dbIdColumnName) == null) {
            int debug = 1;
        }

		return m_mindDb.getEdge(edge.get(sm_dbIdColumnName));
	}

	public void attachChildren (Node parent)
	{
        Tree tree = (Tree)parent.getGraph();

        if (isChildrenAttached(parent)) {
            return;
        }

		ArrayList<EdgeVertex> edgeVertexArray = m_mindDb.getChildrenAndReferents(getDBVertex(parent));

		if (edgeVertexArray == null || edgeVertexArray.size() == 0)
		{
			return;
		}

        if (edgeVertexArray.size() > 50) {
            for (EdgeVertex edgeVertex : edgeVertexArray)
            {
                Vertex parentV = getDBVertex(parent);
                Vertex childV = edgeVertex.m_target;

                s_logger.info("[{}] -> [{}]", parentV.getProperty(TEXT_PROP_NAME), childV.getProperty(TEXT_PROP_NAME));
            }

        }

		for (EdgeVertex edgeVertex : edgeVertexArray)
		{
			Node child = tree.addChild(parent);
			Edge edge = tree.getEdge(parent, child);

			s_logger.info(getDBVertex(parent) + "->" + edgeVertex.m_target + "   :  " + edgeVertex.m_edge);
			loadNodeProperties(edgeVertex.m_target, child);
			loadEdgeProperties(edgeVertex.m_edge, edge);

            verifyNode(child, false);
		}

        verifyNode(parent, true);
        updateChildrenAttached();
	}


    public void detachChildern (Node node)
    {
        Tree tree = (Tree)node.getGraph();
        tree.removeDescendants(node);
    }

	interface NodeVisitor {
	 	abstract public void visit(Node node);
	}

    interface EdgeVisitor {
        abstract public void visit(Edge edge);
    }

    static ArrayList<Integer> getNodeAvatars(Tree tree, Object dbId)
    {
        Table nodeTable = tree.getNodeTable();
        IntIterator allRows = nodeTable.rows();

        ArrayList<Integer> aimRows = new ArrayList<Integer> ();

        //collect the node with the same parentDbId to aimRows
        while (allRows.hasNext()) {
            int curRow = allRows.nextInt();

            assert (curRow >= 0);

            if (nodeTable.get(curRow, sm_dbIdColumnName).equals(dbId)) {
                aimRows.add(curRow);
            }
        }

        return aimRows;
    }

    static ArrayList<Integer> getEdgeAvatars(Tree tree, Object dbId)
    {
        Table edgeTable = tree.getEdgeTable();
        IntIterator allRows = edgeTable.rows();

        ArrayList<Integer> aimRows = new ArrayList<Integer> ();

        //collect the edge with the same dbId to aimRows
        while (allRows.hasNext()) {
            int curRow = allRows.nextInt();

            assert (curRow >= 0);

            if (edgeTable.get(curRow, sm_dbIdColumnName).equals(dbId)) {
                aimRows.add(curRow);
            }
        }

        return aimRows;
    }

	void visitNodeAvatars(Tree tree, Object dbId, NodeVisitor nodeVisitor)
	{
        assert(dbId != null);

        Table nodeTable = tree.getNodeTable();

        ArrayList<Integer> aimRows = getNodeAvatars(tree, dbId);

		//attach blueprints node, to each node in aimRows
        for (Integer row : aimRows) {
			if (nodeTable.isValidRow(row))
			{
				Node node = tree.getNode(row);
				nodeVisitor.visit(node);
			}
		}
	}

    void visitAllNode(Tree tree, NodeVisitor nodeVisitor)
    {
        Table nodeTable = tree.getNodeTable();
        IntIterator allRows = nodeTable.rows();

        while (allRows.hasNext()) {
            int curRow = allRows.nextInt();

            if (nodeTable.isValidRow(curRow)) {
                Node node = tree.getNode(curRow);
                nodeVisitor.visit(node);
            }
        }
    }

    void visitEdgeAvatars(Tree tree, Object dbId, EdgeVisitor edgeVisitor)
    {
        assert(dbId != null);

        Table edgeTable = tree.getEdgeTable();

        ArrayList<Integer> aimRows = getEdgeAvatars(tree, dbId);

        //attach blueprints edge, to each edge in aimRows
        for (Integer row : aimRows) {
            if (edgeTable.isValidRow(row))
            {
                Edge node = tree.getEdge(row);
                edgeVisitor.visit(node);
            }
        }
    }

    private List<MindDB.EdgeVertexId> getNodeOutEdgeIdPairs(Node sourceNode) {
        return Collections.unmodifiableList(m_mindDb.getOutEdgeVertexIds(getDBVertex(sourceNode)));
    }

    protected void exposeNodeRelation(Node sourceNode, int pos, EdgeVertex toTarget)
    {
        assert sourceNode != null;
        assert sourceNode.isValid();

        //如果原来没有挂上子节点，仅仅更新outEdgeIdPairs
        if (isChildrenAttached(sourceNode)) {

            if (!(pos < sourceNode.getChildCount()
                    && getDbId(sourceNode.getChildEdge(pos)).equals(toTarget.m_edge.getId()))) {
                Tree tree = (Tree)sourceNode.getGraph();

                Node child = tree.addNode();
                Edge edge = tree.addChildEdge(sourceNode, child, pos);

                loadNodeProperties(toTarget.m_target, child);
                loadEdgeProperties(toTarget.m_edge, edge);
            }
        }

    }

    protected void hideNodeRelation(Edge edge)
    {
        Tree tree = (Tree)edge.getGraph();
        Node child = tree.getTargetNode(edge);

        tree.removeChild(child);
    }

    /*
    protected void exposeNodeRelations(Node sourceNode, int pos, List<EdgeVertex> toTargets)
    {
        assert sourceNode != null;
        assert sourceNode.isValid();

        for (int i=0; i<toTargets.size(); i++) {
            exposeNodeRelation(sourceNode, i+pos, toTargets.get(i));
        }
    }
    */

    /*
    protected void hideNodeRelations(List<Object> edgeDbIds)
    {
        for (Object edgeDbId : edgeDbIds) {
            hideNodeRelation(edgeDbId);
        }
    }
    */

    //Maybe there are more than one reference edge link source target
    //The callers of exposeTreeRelation has got target and dbEdge, so pass them as argument
    protected void exposeTreeRelation(final Tree tree, final Object sourceId, final int edgePosInSourceNode,
                                      final EdgeVertex toTarget)
	{
		visitNodeAvatars(tree, sourceId, new NodeVisitor() {
            public void visit(Node sourceNode)
            {
                exposeNodeRelation(sourceNode, edgePosInSourceNode, toTarget);
            }
        });
	}

    private void hideTreeRelation(final Tree tree, final Object edgeDbId)
    {
        visitEdgeAvatars(tree, edgeDbId, new EdgeVisitor() {
            public void visit(Edge edge) {
                hideNodeRelation(edge);
            }
        });
    }

    /*
    protected void exposeTreeRelations(final Tree tree, final Object sourceId, final int edgePosInSourceNode,
                                      final List<EdgeVertex> toTargets)
    {
        final Vertex sourceVertex = m_mindDb.getVertex(sourceId);

        visitNodeAvatars(tree, sourceId, new NodeVisitor() {
            public void visit(Node sourceNode)
            {
                exposeNodeRelations(sourceNode, edgePosInSourceNode, toTargets);
            }
        });
    }

    private void hideTreeRelations(final Tree tree, final Object sourceId, final List<Object> edgeDbIds)
    {
        visitNodeAvatars(tree, sourceId, new NodeVisitor() {
            public void visit(Node sourceNode)
            {
                hideNodeRelations(sourceNode, edgeDbIds);
            }
        });
    }
    */

    //opNode: user operated node
    protected void exposeModelRelation(Object sourceId, int pos, EdgeVertex edgeVertex)
    {
        //TODO: 重构时防止拉下，
        assert sourceId instanceof ORecordId;

        for (Tree tree : m_trees) {
            exposeTreeRelation(tree, sourceId, pos, edgeVertex);
        }
    }

    private void hideModelRelation(Object edgeDbId)
    {
        for (final Tree tree : m_trees) {
            hideTreeRelation(tree, edgeDbId);
        }
    }

    /*
    //opNode: user operated node
    protected void exposeModelRelations(Node opNode, int pos, List<EdgeVertex> toTargets)
    {
        verifyNode(opNode, true);
    }

    private void hideModelRelations(Object sourceId, List<Object> edgeDbIds)
    {
        for (final Tree tree : m_trees) {
            hideTreeRelations(tree, sourceId, edgeDbIds);
        }
    }
    */

    //return new child node
	public Node addChild(Node parent, int pos, String text)
    {
        if (! isChildrenAttached(parent)) {
            attachChildren(parent);
        }

        Object parentDbId = getDbId(parent);
		Vertex dbParent = m_mindDb.getVertex(parentDbId);
		EdgeVertex edgeVertex = m_mindDb.addChild(dbParent, pos);

        edgeVertex.m_target.setProperty(TEXT_PROP_NAME, text);

        exposeModelRelation(parentDbId, pos, edgeVertex);

        updateChildrenAttached();

        return parent.getChild(pos);
	}

    private Importer getImporter(String path)
    {
        if (path == null) {
            return new TikaPlainTextImporter(m_mindDb);
        }

        File file = new File(path);
        if (file.isDirectory()) {
            return new DirectoryImporter(m_mindDb);

        } else if (file.isFile()) {
            if (path.endsWith(".mm")) {
                return new FreemindImporter(m_mindDb);
            } else {
                return new TikaPlainTextImporter(m_mindDb);
            }
        } else {
            return null;
        }
    }

    //return new child node
    public List importFile(final Node parent, final String path, final Component progressMonitorParent) throws Exception
    {
        if (! isChildrenAttached(parent)) {
            attachChildren(parent);
        }

        final int pos = parent.getChildCount();

        Object parentDbId = getDbId(parent);
        final Importer importer = getImporter(path);

        if (importer == null) {
            return new ArrayList();
        }

        List newChildren;

        Thread.sleep(5000);
        SwingWorkerDialog swingWorkerDialog = new SwingWorkerDialog(progressMonitorParent, "import :" + path) {
            @Override
            protected Object doInBackground() throws Exception {
                importer.setProgressListener(new Importer.ProgressListener() {
                    @Override
                    public void notifyProgress(int progress, int maxProgress, String message) {
                        notifyProgressA(progress, maxProgress, message);
                        if (isCancelButtonPressed()) {
                            importer.cancel();
                        }
                    }
                });
                return importer.importFile(getDbId(parent), pos, path);
            }
        };

        newChildren = (List)swingWorkerDialog.executeInProgressDialog();

        Vertex dbParent = m_mindDb.getVertex(parentDbId);
        ArrayList<EdgeVertex> newToTargets = new ArrayList<EdgeVertex>();
        for (int i=0; i<newChildren.size(); i++) {
            EdgeVertex edgeVertex = m_mindDb.getChildOrReferent(dbParent, pos+i);
            newToTargets.add(edgeVertex);
        }

        int newVertexPos = pos;
        for (EdgeVertex edgeVertex : newToTargets) {
            exposeModelRelation(parentDbId, newVertexPos, edgeVertex);
            newVertexPos++;

        }

        updateChildrenAttached();
        return newChildren;
    }

    //return new child node
    public List importText(final Node parent, final String text) throws Exception
    {
        if (! isChildrenAttached(parent)) {
            attachChildren(parent);
        }

        final int pos = parent.getChildCount();

        Object parentDbId = getDbId(parent);
        final Importer importer = getImporter(null);

        if (importer == null) {
            return new ArrayList();
        }

        List newChildren;

        if (text != null && !text.isEmpty()) {
            newChildren = importer.importString(getDbId(parent), pos, text);
        } else {
            newChildren = new ArrayList();
        }

        Vertex dbParent = m_mindDb.getVertex(parentDbId);
        ArrayList<EdgeVertex> newToTargets = new ArrayList<EdgeVertex>();
        for (int i=0; i<newChildren.size(); i++) {
            EdgeVertex edgeVertex = m_mindDb.getChildOrReferent(dbParent, pos+i);
            newToTargets.add(edgeVertex);
        }

        int newVertexPos = pos;
        for (EdgeVertex edgeVertex : newToTargets) {
            exposeModelRelation(parentDbId, newVertexPos, edgeVertex);
            newVertexPos++;

        }

        updateChildrenAttached();
        return newChildren;
    }

	//return the DBid of node
	public Object trashNode(Object dbId)
	{
        //删除显示子树
        Iterator<Tree> treeIterator = m_trees.iterator();
        while (treeIterator.hasNext()) {
            Tree tree = treeIterator.next();
            Object treeRootId = tree.getRoot().get(sm_dbIdColumnName);
            if (m_mindDb.subTreeContainsVertexId(dbId, treeRootId)) {
                treeIterator.remove();
            }
        }

        Vertex vertex = m_mindDb.getVertex(dbId);
        MindDB.EdgeVertexId  edgeParentId = m_mindDb.getParentEdgeId(dbId);

        m_mindDb.trashSubTree(m_mindDb.getVertex(dbId));
        removeFromFavorite(dbId);

        hideModelRelation(edgeParentId.m_edgeId);

        final MindDB.TrashedTreeContext context = m_mindDb.getTrashedTreeContext(vertex);
        for (final RefLinkInfo refLinkInfo : context.m_refLinkInfos) {
            hideModelRelation(refLinkInfo.m_edge);
        }

        updateChildrenAttached();

        return dbId;
	}

    public boolean isVertexTrashed(Object dbId)
    {
        Vertex vertex = m_mindDb.getVertex(dbId);
        return m_mindDb.isVertexTrashed(vertex);
    }

	public void restoreNodeFromTrash(final Object dbId)
	{
        /*TODO: 放到外面
        if (! isChildrenAttached(parent)) {
            attachChildren(parent);
        }
        */

        final Vertex restoredVertex = m_mindDb.getVertex(dbId);
        final MindDB.TrashedTreeContext context = m_mindDb.getTrashedTreeContext(restoredVertex);

        final EdgeVertex restoredEdgeVertex = m_mindDb.restoreTrashedSubTree(restoredVertex);

        exposeModelRelation(context.m_parentId, context.m_pos, restoredEdgeVertex);

        for (final RefLinkInfo refLinkInfo : context.m_refLinkInfos) {
            final Vertex referrerVertex = m_mindDb.getVertex(refLinkInfo.m_referrer);
            final EdgeVertex toReferent = m_mindDb.getChildOrReferent(referrerVertex, refLinkInfo.m_pos);

            //不能用 refLink.Info.m_edge, 边会删除、重新添加。与已保存的不同

            exposeModelRelation(refLinkInfo.m_referrer, refLinkInfo.m_pos, toReferent);
        }
        updateChildrenAttached();
	}

    public void addReference(Node referrerNode, int pos, Object referentDbId) {
        if (! isChildrenAttached(referrerNode)) {
            attachChildren(referrerNode);
        }

        s_logger.info(String.format("addReference : %s -- %s", getText(referrerNode), referentDbId.toString()));
        Object referrerDbId  = getDbId(referrerNode);
        Vertex referrerVertex = m_mindDb.getVertex(referrerDbId);
        Vertex referentVertex = m_mindDb.getVertex(referentDbId);
        EdgeVertex edgeVertex = m_mindDb.addRefEdge(referrerVertex, referentVertex, pos);

        exposeModelRelation(getDbId(referrerNode), pos, edgeVertex);
        updateChildrenAttached();
    }


    public void removeReference(Object edgeId) {
        m_mindDb.removeRefEdge(m_mindDb.getEdge(edgeId));
        hideModelRelation(edgeId);
        updateChildrenAttached();
    }

    public void changeChildPos(final Object edgeId, final int newPos)
    {
        s_logger.info("arg: edgeId{}: newPos:{}", edgeId, newPos);

        com.tinkerpop.blueprints.Edge edge = m_mindDb.getEdge(edgeId);
        m_mindDb.changeEdgePos(edge, newPos);

        final MindDB.EdgeVertexId edgeVertexId = m_mindDb.getEdgeVertexId(edge);

        s_logger.info("after change: EdgeVertexId:{}", edgeVertexId);

        for (final Tree tree : m_trees) {
            visitEdgeAvatars(tree, edgeId,
                    new EdgeVisitor() {
                        public void visit(Edge edge) {

                            Node sourceNode = tree.getSourceNode(edge);
                            Node targetNode = tree.getTargetNode(edge);

                            edge.set(EDGE_INNER_ID_PROP_NAME, edgeVertexId.m_edgeInnerId);

                            int oldPos = targetNode.getIndex();
                            tree.changeChildIndex(sourceNode, oldPos, newPos);

                            verifyNode(sourceNode, false);
                        }
                    });
        }

        s_logger.info("ret:");
        updateChildrenAttached();
    }

    public boolean canDragTo(Node node, Node newParent)
    {
        assert(node.getGraph() == newParent.getGraph());
        assert(node.getParent() != null);

        Node oldParent = node.getParent();
        Graph graph = node.getGraph();

        if (isRefEdge(graph.getEdge(oldParent, node))) {
            return true;
        } else {
            return (! subTreeContainsInDB(node, newParent)) && (! isSelfInDB(node.getParent(), newParent));
        }
    }


	public void setProperty(final Object dbId, final String key, final Object value)
	{
		Vertex dbNode = m_mindDb.getVertex(dbId);
        if (value == null) {
            dbNode.removeProperty(key);
        } else {
            assert (TypeLib.typeCheck(sm_propertyClassMap.get(key), value));
            dbNode.setProperty(key, value);
        }

        for (Tree tree : m_trees) {
            visitNodeAvatars(tree, dbId, new NodeVisitor() {
                public void visit(Node node)
                {
                    node.set(key, value);
                    verifyNode(node, false);
                }
            });
        }
	}

    public Object getProperty (final Object dbId, final String key)
    {
        Vertex dbNode = m_mindDb.getVertex(dbId);
        return dbNode.getProperty(key);
    }

	static public Object getDbId(Tuple tuple)
	{
		return tuple.get(sm_dbIdColumnName);
	}

    static public String getOutEdgeInnerId(Edge edge)
    {
        return (String)edge.get(EDGE_INNER_ID_PROP_NAME);
    }

    public int getDBChildCount(Node node)
    {
        return getNodeOutEdgeIdPairs(node).size();
    }

    public boolean isChildrenAttached(Node node)
    {
        return node.getBoolean(CHILDREN_ATTACHED_PROP_NAME);
        /*
        assert  node.getChildCount() == 0 || node.getChildCount() == getNodeOutEdgeIdPairs(node).size();
        return getNodeOutEdgeIdPairs(node).size() == node.getChildCount();
        */
    }

    void updateChildrenAttached()
    {
        for (Tree tree: m_trees) {

            visitAllNode(tree, new NodeVisitor() {
                @Override
                public void visit(Node node) {
                    if (!(node.getChildCount() == 0 || node.getChildCount() == getNodeOutEdgeIdPairs(node).size())) {
                        int debug = 1;
                    }
                    assert  node.getChildCount() == 0 || node.getChildCount() == getNodeOutEdgeIdPairs(node).size();
                    boolean childrenAttached = getNodeOutEdgeIdPairs(node).size() == node.getChildCount();
                    node.setBoolean(CHILDREN_ATTACHED_PROP_NAME, childrenAttached);
                }
            });
        }
    }


    public boolean isSelfInDB(Node n1, Node n2)
    {
        return m_mindDb.vertexIdIsSelf(getDbId(n1), getDbId(n2));
    }

    public boolean isParentOfInDB(Node thiz, Node that) {
        return m_mindDb.vertexIdIsParentOf(getDbId(thiz), getDbId(that));
    }

    public boolean isChildOfInDB(Node thiz, Node that) {
        return m_mindDb.vertexIdIsChildOf(getDbId(thiz), getDbId(that));
    }

    public boolean isSiblingOfInDB(Node thiz, Node that) {
        return m_mindDb.vertexIdIsSiblingOf(getDbId(thiz), getDbId(that));
    }

    public boolean isAncestorOfInDB(Node thiz, Node that) {
        return m_mindDb.vertexIdIsAncestorOf(getDbId(thiz), getDbId(that));
    }

    public boolean isDescendantOfInDB(Node thiz, Node that) {
        return m_mindDb.vertexIdIsDescendantOf(getDbId(thiz), getDbId(that));
    }

    public boolean subTreeContainsInDB(Node n1, Node n2)
    {
        return m_mindDb.subTreeContainsVertexId(getDbId(n1), getDbId(n2));
    }

    private String objectToString(Object object)
    {
        return object == null ? "null" : object.toString();
    }
    public String getNodeDebugInfo(Node node) {
        int row = node.getRow();
        ArrayList<Integer> nodePath = getNodePath(node);
        Object rootId = getDbId(((Tree)node.getGraph()).getRoot());


        Object dbId = getDbId(node);
        if (dbId != null) {
            List inheritPath = m_mindDb.getInheritPath(dbId);
            String infoFmt = "row:%d, rootId:%s, nodePath:%s, inheritPath:%s, id:%s, text:%s";
            return String.format(infoFmt,
                    row,
                    objectToString(rootId),
                    objectToString(nodePath),
                    objectToString(inheritPath),
                    objectToString(getDbId(node)),
                    objectToString(getText(node)));
        } else {
            String infoFmt = "row:%d, rootId:%s, nodePath:%s, placeholder";
            return String.format(infoFmt,
                    row,
                    objectToString(rootId),
                    objectToString(nodePath));
        }
    }

    //not use dbId for argument, becase the node saved the propperty
    static public String getText(Node node)
    {
        return node.getString(TEXT_PROP_NAME);
    }

    public String getContextText(Object dbId)
    {
        Vertex vertex = m_mindDb.getVertex(dbId);
        String text = vertex.getProperty(TEXT_PROP_NAME);
        EdgeVertex parent = m_mindDb.getParentEge(vertex);

        if (parent == null) {
            return text;
        } else {
            String parentText = parent.m_source.getProperty(TEXT_PROP_NAME);
            return parentText + " -> " + text;
        }
    }

    public boolean isRefEdge(Edge edge)
    {
        return MindDB.EdgeType.values()[(Integer)edge.get(sm_edgeTypePropName)] == MindDB.EdgeType.REFERENCE;
    }

    public boolean isRefNode(Node node)
    {
        Tree tree = (Tree)node.getGraph();
        Node parent = node.getParent();
        Edge edge = tree.getEdge(parent, node);
        return isRefEdge(edge);
    }


    public ArrayList<Integer> getNodePath(Node node)
    {
        Tree tree = (Tree)node.getGraph();
        ArrayList<Integer> path = new ArrayList<Integer>();

        Node climber = node;
        Node root = tree.getRoot();

        assert (climber != null);

        while (climber != root)
        {
            path.add(0, tree.getIndexInSiblings(climber));
            climber = climber.getParent();
            if (climber.getRow()==root.getRow() && climber != root) {
                s_logger.info("aaaaaaaaaaaa");
            }
        }

        return path;
    }

    public Node getNodeByPath(Tree tree, ArrayList<Integer> path)
    {
        Node node = tree.getRoot();

        for (int i=0; i<path.size(); i++) {

            if (node.getChildCount() == 0 && getDBChildCount(node) > 0) {
                attachChildren(node);
            }

            int pos = path.get(i);
            node = node.getChild(pos);

            assert(node != null);

            if (node == null) {
                return null;
            }
        }

        return node;
    }

    public Node getNodeByPath(Tree tree, int[] path)
    {
        Node node = tree.getRoot();

        for (int i=0; i<path.length; i++) {

            if (! isChildrenAttached(node)) {
                attachChildren(node);
            }

            int pos = path[i];
            node = node.getChild(pos);

            if (node == null) {
                return null;
            }
        }

        return node;
    }

    public boolean isInFavorite(Object dbId) {
        for (VertexBasicInfo info: m_favoriteInfoes) {
            if (info.m_dbId.equals(dbId)) {
                return true;
            }
        }
        return false;
    }

    public void addToFavorite(Object dbId) {
        m_favoriteIndex.put(FAVORITE_KEY_NAME, FAVORITE_KEY_NAME, m_mindDb.getVertex(dbId));

        assert(!isInFavorite(dbId));
        m_favoriteInfoes.add(new VertexBasicInfo(m_mindDb.getVertex(dbId)));
    }

    public void removeFromFavorite(Object dbId) {
        m_favoriteIndex.remove(FAVORITE_KEY_NAME, FAVORITE_KEY_NAME, m_mindDb.getVertex(dbId));

        for (VertexBasicInfo info: m_favoriteInfoes) {
            if (info.m_dbId.equals(dbId)) {
                m_favoriteInfoes.remove(info);
                break;
            }
        }
    }

    //TODO: add cached
    public VertexBasicInfo getVertexBasicInfo(Object dbId) {
        return new VertexBasicInfo(m_mindDb.getVertex(dbId));
    }

    static final String MIRROR_X = "mirrorX";
    static final String MIRROR_Y = "mirrorY";

    //mirror_x, mirror_y 列与 nodeItem中的 VisualItem.X, VisualItem.Y列同步。用于计算Node之间的距离, 在拖动操作中使用
    public static void addNodeMirrorXYColumn(Tree tree, VisualTree visualTree)
    {
        final Table nodeTable = tree.getNodeTable();
        final VisualTable nodeItemTable = (VisualTable)visualTree.getNodeTable();

        nodeTable.addColumn(MIRROR_X, double.class, 0.0);
        nodeTable.addColumn(MIRROR_Y, double.class, 0.0);

        nodeItemTable.addTableListener(new TableListener() {
            @Override
            public void tableChanged(Table t, int start, int end, int col, int type)
            {
                if (type == EventConstants.UPDATE) {
                    if (col == nodeItemTable.getColumnNumber(VisualItem.X)) {
                        for (int row = start; row <= end; row++) {
                            nodeTable.setDouble(row, "mirrorX", nodeItemTable.getX(row));
                        }

                    } else if (col == nodeItemTable.getColumnNumber(VisualItem.Y)) {
                        for (int row = start; row <= end; row++) {
                            nodeTable.setDouble(row, "mirrorY", nodeItemTable.getX(row));
                        }

                    }
                }
            }
        });
    }

    private static double getNodeDistanceSquare(Node node1, Node node2)
    {
        double x1 = node1.getDouble(MIRROR_X);
        double y1 = node1.getDouble(MIRROR_Y);
        double x2 = node2.getDouble(MIRROR_X);
        double y2 = node2.getDouble(MIRROR_Y);

        return (x1 - x2) * (x1 - x2)  + (y1 - y2) * (y1 - y2) ;
    }

    private static class NodeAvatarsPairingInfo {
        HashMap<Integer, Integer> m_nodeAvatarPairs;
        ArrayList<Integer> m_nodeAvatars1Alone;
        ArrayList<Integer> m_nodeAvatars2Alone;

    }

    private NodeAvatarsPairingInfo pairNodeAvatars(Tree tree, Object dbId1, Object dbId2,
                                             int enforceNode1, int enforceNode2)
    {
        s_logger.info("arg: tree:{}", getDbId(tree.getRoot()));
        s_logger.info("arg: dbID1:{}", dbId1);
        s_logger.info("arg: dbID2:{}", dbId2);
        s_logger.info("arg: enforceNode1:{}", enforceNode1);
        s_logger.info("arg: enforceNode2:{}", enforceNode2);

        final ArrayList<Integer> nodeAvatars1 = getNodeAvatars(tree, dbId1);
        final ArrayList<Integer> nodeAvatars2 = getNodeAvatars(tree, dbId2);

        //跳过没有展开子节点的目标父节点
        Iterator<Integer> iterator2 = nodeAvatars2.iterator();
        while (iterator2.hasNext()) {
            int node2 = iterator2.next();
            if (!isChildrenAttached(tree.getNode(node2)) && node2 != enforceNode2) {
                iterator2.remove();
            }
        }

        final HashMap<Integer, Integer> pairs = new HashMap<Integer, Integer>();

        if (enforceNode1 >= 0) {
            assert (nodeAvatars1.contains(enforceNode1));
            assert (nodeAvatars2.contains(enforceNode2));
        }

        if (enforceNode1 >= 0 && enforceNode2 >= 0) {
            pairs.put(enforceNode1, enforceNode2);
            nodeAvatars1.remove((Integer)enforceNode1);
            nodeAvatars2.remove((Integer)enforceNode2);
        }

        //sort by x,y
        Iterator<Integer> iterator1 = nodeAvatars1.iterator();
        while (iterator1.hasNext() && nodeAvatars2.size() > 0) {
            int node1 = iterator1.next();

            if (! isChildrenAttached(tree.getNode(node1))) {
                continue;
            }

            int nearestNode2 = -1;
            Double minDistanceSquare = Double.MAX_VALUE;

            for (int node2 : nodeAvatars2) {

                double distanceSquare = getNodeDistanceSquare(tree.getNode(node1), tree.getNode(node2));
                if (distanceSquare < minDistanceSquare) {
                    minDistanceSquare = distanceSquare;
                    nearestNode2 = node2;
                }
            }

            pairs.put(node1, nearestNode2);

            iterator1.remove();
            nodeAvatars2.remove((Integer)nearestNode2);
        }

        s_logger.info("QQQQQQQQQQQQQQQQQQQQQQQqq");
        NodeAvatarsPairingInfo pairingInfo = new NodeAvatarsPairingInfo();
        s_logger.info("llllllllllllllllllQQQQQQQQQQQQQQQQQQQQQQqq");
        pairingInfo.m_nodeAvatarPairs = pairs;
        pairingInfo.m_nodeAvatars1Alone = nodeAvatars1;
        pairingInfo.m_nodeAvatars2Alone = nodeAvatars2;

        s_logger.info("ret: {}", pairingInfo);

        return pairingInfo;
    }

    private void rebuildChildEdge(Tree tree, NodeAvatarsPairingInfo oldNewParentPairingInfo,
                                  int oldChildPos, int newChildPos,
                                  EdgeVertex childEdgeVertex, Object oldEdgeDbId)
    {
        HashMap<Integer, Integer> pairs = oldNewParentPairingInfo.m_nodeAvatarPairs;
        for (int node1 : pairs.keySet())
        {
            int node2 = pairs.get(node1);
            Node oldParent = tree.getNode(node1);
            Node newParent = tree.getNode(node2);
            Node child = oldParent.getChild(oldChildPos);

            tree.removeEdge(tree.getEdge(oldParent, child));

            Edge newEdge = tree.addChildEdge(newParent, child, newChildPos);
            loadEdgeProperties(childEdgeVertex.m_edge, newEdge);

            verifyNode(oldParent, true);
            verifyNode(newParent, true);
        }

        for (int node1 : oldNewParentPairingInfo.m_nodeAvatars1Alone) {
            if (tree.getNodeTable().isValidRow(node1)) {
                Node oldParent = tree.getNode(node1);
                //NO, 不是他
                if (isChildrenAttached(oldParent)) {
                    hideNodeRelation(tree.getChildEdge(oldParent, oldChildPos));
                }
                verifyNode(oldParent, false);
            }
        }

        for (int node2 : oldNewParentPairingInfo.m_nodeAvatars2Alone) {
            if (tree.getNodeTable().isValidRow(node2)) {
                Node newParent = tree.getNode(node2);
                exposeNodeRelation(newParent, newChildPos, childEdgeVertex);
                verifyNode(newParent, false);
            }
        }
    }

    private void handoverNode(Object oldParentDbId, int oldPos, Node oldParent,
                                Object newParentDbId, int newPos, Node newParent,
                              EdgeVertex newEdgeVertex, Object oldEdgeDbId)
    {
        if (oldParent != null) {
            assert getDbId(oldParent).equals(oldParentDbId);
        }
        if (newParent != null) {
            assert getDbId(newParent).equals(newParentDbId);
        }


        for (Tree tree : m_trees) {
            NodeAvatarsPairingInfo oldNewParentPairingInfo;
            if (newParent != null && oldParent != null && tree == oldParent.getGraph() )
            {
                oldNewParentPairingInfo = pairNodeAvatars(tree, oldParentDbId, newParentDbId,
                        oldParent.getRow(), newParent.getRow());
            } else {
                oldNewParentPairingInfo = pairNodeAvatars(tree, oldParentDbId, newParentDbId,
                        -1, -1);
            }

            rebuildChildEdge(tree, oldNewParentPairingInfo, oldPos, newPos, newEdgeVertex, oldEdgeDbId);
        }
    }

    //oldSource, newSource是一个可选的参数，如果非空，界面树上会加入一个oldSource的子节点拖动newSource的效果。
    public void handoverRelation(Object oldSourceDbId, int oldPos, Node oldSource,
                                 Object newSourceDbId, int newPos, Node newSource)
    {
        s_logger.info("arg: oldSourceDbId:{}", oldSourceDbId);
        s_logger.info("arg: oldPos:{}", oldPos);
        s_logger.info("arg: oldSource:{}", oldSource);
        s_logger.info("arg: newSourceDbId:{}", newSourceDbId);
        s_logger.info("arg: newPos:{}", newPos);
        s_logger.info("arg: newSource:{}", newSource);

        assert (! oldSourceDbId.equals(newSourceDbId));

        Vertex targetVertex;
        Object oldEdgeId;
        boolean isReferent;

        //通过Node获取比直接用数据查找快
        if (oldSource != null) {
            if (! isChildrenAttached(oldSource)) {
                attachChildren(oldSource);
            }

            Node child = oldSource.getChild(oldPos);
            targetVertex = getDBVertex(child);

            Edge oldEdge = child.getParentEdge();
            oldEdgeId = getDbId(oldEdge);
            isReferent = isRefEdge(oldEdge);

        } else {
            Vertex parentVertex = m_mindDb.getVertex(oldSourceDbId);
            EdgeVertex childEdgeVertex = m_mindDb.getChildOrReferent(parentVertex, oldPos);

            assert childEdgeVertex != null;

            targetVertex = childEdgeVertex.m_target;
            oldEdgeId = childEdgeVertex.m_edge.getId();
            isReferent = m_mindDb.getEdgeType(childEdgeVertex.m_edge) == MindDB.EdgeType.REFERENCE;
        }

        if (! isReferent) {
            assert ! m_mindDb.vertexIdIsInSubTreeOf(newSourceDbId, targetVertex.getId());
        }

        //FIXME: 如果newSource有指向自身的边，此处展开一层对于并不能简化代码。
        if (newSource != null && !isChildrenAttached(newSource)) {
            attachChildren(newSource);
        }

        Vertex newSourceVertex = m_mindDb.getVertex(newSourceDbId);
        if (newPos < 0) {
            newPos = m_mindDb.getChildOrReferentCount(newSourceVertex);
        }

        EdgeVertex newEdgeVertex;
        if (isReferent) {
            newEdgeVertex = m_mindDb.handoverReferent(m_mindDb.getEdge(oldEdgeId), newSourceVertex, newPos);
        } else {
            newEdgeVertex = m_mindDb.handoverChild(targetVertex, newSourceVertex, newPos);
        }
        handoverNode(oldSourceDbId, oldPos, oldSource, newSourceDbId, newPos, newSource, newEdgeVertex, oldEdgeId);

        updateChildrenAttached();
        s_logger.info("ret:");
    }


    public boolean m_verifyNodeEnabled = true;

    void verifyNode(Node node, boolean forceChildAttached)
    {
        if (true) {
            return;
        }

        if (m_verifyNodeEnabled == false) {
            return;
        }

        Vertex vertex = getDBVertex(node);

        m_mindDb.verifyVertex(vertex);

        verifyElementProperties(vertex, node, sm_nodePropNames);

        List<MindDB.EdgeVertexId> outEdgeVertexIds = m_mindDb.getOutEdgeVertexIds(getDBVertex(node));
        if (outEdgeVertexIds.size() > 0 && forceChildAttached) {
            assert node.getChildCount() == outEdgeVertexIds.size();
        }

        for (int i=0; i<node.getChildCount(); i++) {
            Node childOrReferenceNode = node.getChild(i);
            Edge outEdge = (node.getGraph()).getEdge(node, childOrReferenceNode);

            assert(getOutEdgeInnerId(outEdge).equals(outEdgeVertexIds.get(i).m_edgeInnerId));
            verifyElementProperties(getDBEdge(outEdge), outEdge, sm_edgePropNames);

            Integer outEdgeType = (Integer)outEdge.get(sm_edgeTypePropName);
            if (MindDB.EdgeType.values()[outEdgeType] == MindDB.EdgeType.INCLUDE) {
                assert m_mindDb.vertexIdIsChildOf(getDbId(childOrReferenceNode), getDbId(node));
            } else {
                assert MindDB.EdgeType.values()[outEdgeType] == MindDB.EdgeType.REFERENCE;
            }
        }

        Edge inEdge = node.getParentEdge();
        if (inEdge != null) {
            verifyElementProperties(getDBEdge(inEdge), inEdge, sm_edgePropNames);

            Integer inEdgeType = (Integer)inEdge.get(sm_edgeTypePropName);
            Node parentOrReferrerNode = inEdge.getSourceNode();

            List<MindDB.EdgeVertexId> parentOrReferrerOutEdgeIdPairs =
                    m_mindDb.getOutEdgeVertexIds(getDBVertex(parentOrReferrerNode));

            if (node.getIndex() >= parentOrReferrerOutEdgeIdPairs.size()) {
                int debug = 1;
            }

            assert parentOrReferrerOutEdgeIdPairs.get(node.getIndex()).m_edgeInnerId.equals(getOutEdgeInnerId(inEdge));

            if (MindDB.EdgeType.values()[inEdgeType] == MindDB.EdgeType.INCLUDE) {
                assert m_mindDb.vertexIdIsParentOf(getDbId(parentOrReferrerNode), getDbId(node));
            } else {
                assert MindDB.EdgeType.values()[inEdgeType] == MindDB.EdgeType.REFERENCE;
            }
        }
    }

    public static Font getNodeFont(Tuple tuple)
    {
        String family = (String)tuple.get(sm_fontFamilyPropName);
        Integer size = (Integer)tuple.get(sm_fontSizePropName);
        Boolean bold = (Boolean)tuple.get(sm_boldPropName);
        Boolean italic = (Boolean)tuple.get(sm_italicPropName);

        if (family == null) {
            family = Style.getFontFamilySurely(tuple.getString(STYLE_PROP_NAME));
        }

        if (size == null) {
            size = Style.getFontSizeSurely(tuple.getString(STYLE_PROP_NAME));
        }

        if (bold == null) {
            bold = Style.getBoldSurely(tuple.getString(STYLE_PROP_NAME));
        }

        if (italic == null) {
            italic = Style.getItalicSurely(tuple.getString(STYLE_PROP_NAME));
        }

        if (family == null || bold == null || italic == null || size ==null) {
            int debug =1;
        }
        return FontLib.getFont(family, bold, italic, size);
    }

    public static int getNodeColor(Tuple tuple)
    {
        Integer color = (Integer)tuple.get(sm_nodeColorPropName);

        if (color != null) {
            return color;
        }

        return Style.getNodeColorSurely(tuple.getString(STYLE_PROP_NAME));
    }

    public static int getNodeTextColor(Tuple tuple)
    {
        Integer color = (Integer)tuple.get(sm_textColorPropName);

        if (color != null) {
            return color;
        }

        return Style.getTextColorSurely(tuple.getString(STYLE_PROP_NAME));
    }

    public static String getNodeIcon(Tuple tuple)
    {
        String icon = (String)tuple.get(sm_iconPropName);

        if (icon != null) {
            return icon;
        }

        return Style.getIconSurely(tuple.getString(STYLE_PROP_NAME));
    }

    public String getSubTreeText(Node subTreeRoot)
    {
        Tree tree = (Tree)subTreeRoot.getGraph();
        final StringBuilder stringBuilder = new StringBuilder();
        final String newline  = System.getProperty("line.separator");

        Tree.DepthFirstTraverseProcessor appendTextProc = new Tree.DepthFirstTraverseProcessor() {
            public boolean run(Node parent, Node node, int level) {
                for (int i=0; i<level; i++) {
                    stringBuilder.append("    ");
                }
                stringBuilder.append(getText(node));
                stringBuilder.append(newline);
                return true;
            }
        };

        tree.depthFirstTraverse(subTreeRoot, appendTextProc);
        return stringBuilder.toString();
    }

    private void pasteNodeRecursively(final Node externalNode, final Node newNode)
    {
        visitNodeAvatars((Tree)newNode.getGraph(), getDbId(newNode), new NodeVisitor() {
            @Override
            public void visit(Node newNodeAvatar)
            {
                Table.copyTuple(externalNode, newNodeAvatar, sm_nodePropNames);
                if (newNodeAvatar == newNode) {
                    storeNodeProperties(getDBVertex(newNodeAvatar), newNodeAvatar);
                }

                Edge externalEdgeToParent = externalNode.getParentEdge();
                if (externalEdgeToParent != null) {
                    Edge newAvatarEdgeToParent = newNodeAvatar.getParentEdge();

                    Table.copyTuple(externalEdgeToParent, newAvatarEdgeToParent, sm_edgePropNames);
                    if (newNodeAvatar == newNode) {
                        storeEdgeProperties(getDBEdge(newAvatarEdgeToParent), newAvatarEdgeToParent);
                    }
                }
            }
        });

        for(int i=0; i<externalNode.getChildCount(); i++) {
            Node newChild = addChild(newNode, i, "pasteNodeRecursively");
            pasteNodeRecursively(externalNode.getChild(i), newChild);
        }
    }

    public Node pasteTree(Node pastePoint, int position, Tree externalTree)
    {
        if (externalTree == null || externalTree.getRoot() == null) {
            return null;
        }

        Node subTreeRoot = addChild(pastePoint, position, "pasteTree");
        pasteNodeRecursively(externalTree.getRoot(), subTreeRoot);
        updateChildrenAttached();
        return subTreeRoot;
    }

    public String getVertexDbIdInheritInfo(Object dbId) {
        List inheritPath = m_mindDb.getInheritPath(dbId);
        String info = "";
        for (Object ancestor : inheritPath) {
            Vertex vertex  = m_mindDb.getVertex(ancestor);
            info += vertex.getProperty(TEXT_PROP_NAME) + " > ";
        }

        Vertex vertex  = m_mindDb.getVertex(dbId);
        info += vertex.getProperty(TEXT_PROP_NAME);
        return info;
    }
}
