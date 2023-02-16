package authentification.mapping;

public interface Mappable<T> {
    void insert(T t);   // C
    T find(Integer id); // R
    T find(T t);
    void update(T t);   // U
    void delete(T t);   // D
    void delete(Integer id);
}