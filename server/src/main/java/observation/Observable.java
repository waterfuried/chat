/*
    пакет содержит поведенческий шаблон проектирования Наблюдатель (Observer),
    ранее уже реализованный в проекте
*/
package observation;

public interface Observable {
    void subscribe(Observer o);
    void unsubscribe(Observer o);
    void notifyObservers();
}