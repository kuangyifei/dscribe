package com.ideanest.dscribe.mixt;

import java.util.Collection;

public interface SortingBlock<T extends Seg> extends Block {
	void sort(Collection<T> segs, SortController.OrderGraph graph);
}
