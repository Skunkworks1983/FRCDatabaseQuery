package com.skunkworks.util;

import java.util.Iterator;

public final class FilteredIterator<E> implements Iterator<E> {

	private final Filter<E> filter;

	private E eNext;

	private final Iterator<E> backing;

	public FilteredIterator(final Iterator<E> sBacking, final Filter<E> sFilter) {
		this.backing = sBacking;
		this.filter = sFilter;
		this.eNext = getNext();
	}

	public boolean hasNext() {
		return eNext != null;
	}

	public E next() {
		E cache = eNext;
		eNext = getNext();
		return cache;
	}

	private E getNext() {
		while (true) {
			if (!backing.hasNext()) {
				return null;
			}
			E temp = backing.next();
			if (temp == null) {
				return null;
			} else if (filter.accept(temp)) {
				return temp;
			}
		}
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}
};
