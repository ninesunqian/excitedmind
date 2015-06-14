package prefuse.visual.tuple;

import java.util.Iterator;

import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.data.Table;
import prefuse.visual.NodeItem;

/**
 * NodeItem implementation that used data values from a backing
 * VisualTable of nodes.
 * 
 * @author <a href="http://jheer.org">jeffrey heer</a>
 */
public class TableNodeItem extends TableVisualItem implements NodeItem {

    protected Graph m_graph;

    /**
     * Initialize a new TableNodeItem for the given graph, table, and row.
     * This method is used by the appropriate TupleManager instance, and
     * should not be called directly by client code, unless by a
     * client-supplied custom TupleManager.
     * @param table the backing VisualTable
     * @param graph the backing VisualGraph
     * @param row the row in the node table to which this Node instance
     *  corresponds.
     */
    protected void init(Table table, Graph graph, int row) {
        m_table = table;
        m_graph = graph;
        m_row = m_table.isValidRow(row) ? row : -1;
    }
    
    /**
     * @see prefuse.data.Node#getGraph()
     */
    public Graph getGraph() {
        return m_graph;
    }
    
    // ------------------------------------------------------------------------
    // If only we had multiple inheritance or categories....
    // Instead we must re-implement the entire Node interface.
    
    /**
     * @see prefuse.data.Node#getInDegree()
     */
    public int getInDegree() {
        return m_graph.getInDegree(this);
    }

    /**
     * @see prefuse.data.Node#getOutDegree()
     */
    public int getOutDegree() {
        return m_graph.getOutDegree(this);
    }

    /**
     * @see prefuse.data.Node#getDegree()
     */
    public int getDegree() {
        return m_graph.getDegree(this);
    }

    /**
     * @see prefuse.data.Node#inEdges()
     */
    public Iterator inEdges() {
        return m_graph.inEdges(this);
    }

    /**
     * @see prefuse.data.Node#outEdges()
     */
    public Iterator outEdges() {
        return m_graph.outEdges(this);
    }
    
    /**
     * @see prefuse.data.Node#edges()
     */
    public Iterator edges() {
        return m_graph.edges(this);
    }
    
    /**
     * @see prefuse.data.Node#inNeighbors()
     */
    public Iterator inNeighbors() {
        return m_graph.inNeighbors(this);
    }
    
    /**
     * @see prefuse.data.Node#outNeighbors()
     */
    public Iterator outNeighbors() {
        return m_graph.outNeighbors(this);
    }
    
    /**
     * @see prefuse.data.Node#neighbors()
     */
    public Iterator neighbors() {
        return m_graph.neighbors(this);
    }

    // ------------------------------------------------------------------------
    
    /**
     * @see prefuse.data.Node#getParent()
     */
    public Node getParent() {
        return m_graph.getSpanningTree().getParent(this);
    }

    /**
     * @see prefuse.data.Node#getParentEdge()
     */
    public Edge getParentEdge() {
        return m_graph.getSpanningTree().getParentEdge(this);
    }
    
    /**
     * @see prefuse.data.Node#getChildCount()
     */
    public int getChildCount() {
        return m_graph.getSpanningTree().getChildCount(m_row);
    }

    /**
     * @see prefuse.data.Node#getChildIndex(prefuse.data.Node)
     */
    public int getChildIndex(Node child) {
        return m_graph.getSpanningTree().getChildIndex(this, child);
    }
    
    /**
     * @see prefuse.data.Node#getChild(int)
     */
    public Node getChild(int idx) {
        return m_graph.getSpanningTree().getChild(this, idx);
    }

    public Edge getChildEdge(int idx) {
        return m_graph.getSpanningTree().getChildEdge(this, idx);
    }

    /**
     * @see prefuse.data.Node#getFirstChild()
     */
    public Node getFirstChild() {
        return m_graph.getSpanningTree().getFirstChild(this);
    }
    
    /**
     * @see prefuse.data.Node#getLastChild()
     */
    public Node getLastChild() {
        return m_graph.getSpanningTree().getLastChild(this);
    }
    
    /**
     * @see prefuse.data.Node#getPreviousSibling()
     */
    public Node getPreviousSibling() {
        return m_graph.getSpanningTree().getPreviousSibling(this);
    }
    
    /**
     * @see prefuse.data.Node#getNextSibling()
     */
    public Node getNextSibling() {
        return m_graph.getSpanningTree().getNextSibling(this);
    }
    
    /**
     * @see prefuse.data.Node#children()
     */
    public Iterator children() {
        return m_graph.getSpanningTree().children(this);
    }

    /**
     * @see prefuse.data.Node#childEdges()
     */
    public Iterator childEdges() {
        return m_graph.getSpanningTree().childEdges(this);
    }

    /**
     * @see prefuse.data.Node#getDepth()
     */
    public int getDepth() {
        return m_graph.getSpanningTree().getDepth(m_row);
    }

    /**
     * @see prefuse.data.Node#getIndex()
     */
    public int getIndex()
    {
        //TODO change to using outlinks inlinks
        return m_graph.getSpanningTree().getIndexInSiblings(this);
    }

    public boolean isParentOf(Node that) {
        return m_graph.getSpanningTree().isParentOf(this, that);
    }

    public boolean isChildOf(Node that) {
        return m_graph.getSpanningTree().isChildOf(this, that);
    }

    public boolean isSiblingOf(Node that) {
        return m_graph.getSpanningTree().isSiblingOf(this, that);
    }

    public boolean isDescendantOf(Node that) {
        return m_graph.getSpanningTree().isDescendantOf(this, that);
    }

    public boolean isAncestorOf(Node that) {
        return m_graph.getSpanningTree().isAncestorOf(this, that);
    }

    public boolean subTreeContains(Node node) {
        return m_graph.getSpanningTree().subTreeContains(this, node);
    }

} // end of class TableNodeItem
