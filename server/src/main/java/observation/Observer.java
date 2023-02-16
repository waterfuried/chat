/*
    пакет содержит поведенческий шаблон проектирования Наблюдатель (Observer),
    ранее уже реализованный в проекте
*/
package observation;

public interface Observer {
    void update(String message);
}