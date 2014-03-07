package com.skunkworks.util;

public interface Filter<E> {

	boolean accept(E e);
}
