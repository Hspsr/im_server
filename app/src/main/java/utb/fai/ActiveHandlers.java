package utb.fai;

import java.util.*;

public class ActiveHandlers {
    private static final long serialVersionUID = 1L;
    private HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();

    void sendMessageToSelf(SocketHandler sender, String message) {
        if (!sender.messages.offer(message))
            System.err.printf("Client %s message queue is full, dropping the message!\n", sender.clientID);
    }

    /**
     * sendMessageToAll - Pole zprávu vem aktivním klientùm kromì sebe sama
     * 
     * @param sender  - reference odesílatele
     * @param message - øetìzec se zprávou
     */
    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet) // pro vechny aktivní handlery
            if (handler != sender & !Collections.disjoint(sender.rooms, handler.rooms)) {
                if (!handler.messages.offer(message)) // zkus pøidat zprávu do fronty jeho zpráv
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
    }

    synchronized boolean sendMessageToName(String message, String name) {
        for (SocketHandler handler : activeHandlersSet) {
            if (handler.name.equals(name)) {
                if (!handler.messages.offer(message))
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
                return true;
            }
        }
        return false;
    }

    synchronized boolean isNameTaken(String name) {
        for (SocketHandler handler : activeHandlersSet) {
            if (name.equals(handler.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * add pøidá do mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má pøidat.
     * @return true if the set did not already contain the specified element.
     */
    synchronized boolean add(SocketHandler handler) {
        return activeHandlersSet.add(handler);
    }

    /**
     * remove odebere z mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má odstranit
     * @return true if the set did not already contain the specified element.
     */
    synchronized boolean remove(SocketHandler handler) {
        return activeHandlersSet.remove(handler);
    }
}
