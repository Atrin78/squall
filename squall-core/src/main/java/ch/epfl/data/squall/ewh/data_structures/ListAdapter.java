package ch.epfl.data.squall.ewh.data_structures;

public interface ListAdapter<T extends Comparable<T>> {
	public void add(T t);

	public T get(int index);

	public void remove(int index);

	public void set(int index, T t);

	public int size();

	public void sort();
}