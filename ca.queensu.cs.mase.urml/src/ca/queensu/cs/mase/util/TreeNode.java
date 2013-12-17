package ca.queensu.cs.mase.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TreeNode<T> implements Iterable<TreeNode<T>> {

	public T data;
	public TreeNode<T> parent;
	public List<TreeNode<T>> children;
	
	public boolean isRoot() {
		return parent == null;
	}
	
	public boolean isLeaf() {
		return children.size() == 0;
	}
	
	private List<TreeNode<T>> elementsIndex;
	
	public TreeNode(T data) {
		this.data = data;
		this.children = new LinkedList<>();
		this.elementsIndex = new LinkedList<>();
		this.elementsIndex.add(this);
	}
	
	public TreeNode<T> addChild(T child) {
		TreeNode<T> childNode = new TreeNode<T>(child);
		childNode.parent = this;
		this.children.add(childNode);
		this.registerChildForSearch(childNode);
		return childNode;
	}
	
	public TreeNode<T> findRoot() {
		if (this.isRoot()) {
			return this;
		} else {
			return parent.findRoot();
		}
	}
	
	private void registerChildForSearch(TreeNode<T> node) {
		elementsIndex.add(node);
		if (parent != null)
			parent.registerChildForSearch(node);
	}
	
	public int getLevel() {
		if (this.isRoot())
			return 0;
		else
			return parent.getLevel() - 1;
	}
	
	public TreeNode<T> findTreeNode(Comparable<T> cmp) {
		for (TreeNode<T> element : this.elementsIndex) {
			T elData = element.data;
			if (cmp.compareTo(elData) == 0)
				return element;
		}
		return null;
	}
	
	@Override
	public String toString() {
		return data != null ? data.toString() : "[data null]";
	}
	
	@Override
	public Iterator<TreeNode<T>> iterator() {
		TreeNodeIter<T> iter = new TreeNodeIter<T>(this);
		return iter;
	}
}
