package com.ideanest.dscribe.mixt;

import java.util.Collection;

public interface SortingBlock<T extends Seg> {
	void sort(Collection<T> segs, SortController.OrderGraph graph);
}
