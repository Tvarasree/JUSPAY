import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

class TestClass {

    //class representing each node in the tree structure
    static class TreeNode {
        String name;//unique name of the node
        boolean isLocked = false; //whether the node is currently locked
        int id; //user id
        TreeNode parent;//pointer to the parent node

        int anc_locked = 0; //no. of locked ancestors
        int des_locked = 0; //no. of locked descendants

        boolean inUse = false;    //true if this node is currently being modified by a thread/task
        Queue<Runnable> queue = new LinkedList<>(); //queue of pending operations on this node

        ArrayList<TreeNode> child = new ArrayList<>(); //child nodes

        TreeNode(String name, TreeNode parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    static Set<TreeNode> lockedNode = new HashSet<>(); //keeps track of all currently locked nodes
    static Map<String, TreeNode> map = new HashMap<>(); //maps node names to TreeNode objects

    public static void main(String args[]) throws Exception {
        Scanner sc = new Scanner(System.in);

        int n = Integer.parseInt(sc.nextLine()); //total number of nodes in the tree
        int m = Integer.parseInt(sc.nextLine()); //max number of children per node
        int q = Integer.parseInt(sc.nextLine()); //number of queries to process

        //read node names , store them
        String[] nodes = new String[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = sc.nextLine();
        }

        //build the tree structure in level-order 
        TreeNode root = new TreeNode(nodes[0], null);
        map.put(nodes[0], root);
        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(root);
        int ind = 1;

        //assign children to nodes as per m childs constraint
        while (!queue.isEmpty() && ind < n) {
            int size = queue.size();
            while (size-- > 0) {
                TreeNode parent = queue.poll();
                for (int i = 0; i < m && ind < n; i++) {
                    TreeNode node = new TreeNode(nodes[ind], parent);
                    parent.child.add(node);
                    map.put(nodes[ind], node);
                    queue.add(node);
                    ind++;
                }
            }
        }

        //process each query
        for (int i = 0; i < q; i++) {
            String[] parts = sc.nextLine().split(" ");
            int type = Integer.parseInt(parts[0]);
            String nodeName = parts[1];       
            int uid = Integer.parseInt(parts[2]); 

            TreeNode node = map.get(nodeName);
            int finalType = type;

            //combining the logic into a Runnable task
            Runnable task = () -> {
                node.inUse = true; //mark node as being processed
                boolean result = false;

                //perform the respective operation
                if (finalType == 1) {
                    result = lock(node, uid);
                } else if (finalType == 2) {
                    result = unlock(node, uid);
                } else if (finalType == 3) {
                    result = upgrade(node, uid);
                }

                System.out.println(result); //print the result (true or false)
                node.inUse = false;         //mark node as free
                runNext(node);              //run the next task in the queue, if any
            };

            //if node is free, run the task immediately,else enqueue it
            if (!node.inUse) {
                task.run();
            } else {
                node.queue.add(task);
            }
        }
    }

    //run the next pending task in the queue for a node, if it exists
    static void runNext(TreeNode node) {
        if (!node.queue.isEmpty()) {
            Runnable next = node.queue.poll();
            next.run();
        }
    }

    //lock the given node if conditions are met
    static boolean lock(TreeNode node, int id) {
        //cannot lock if the node is already locked or if any ancestors or descendants are locked
        if (node.isLocked || node.anc_locked > 0 || node.des_locked > 0) {
            return false;
        }

        //increment des_locked count in all ancestors
        TreeNode cur = node.parent;
        while (cur != null) {
            waitAndRun(cur); //wait for safe access to cur
            cur.des_locked++;
            runNext(cur);
            cur = cur.parent;
        }

        //increment anc_locked count for all descendants
        informDescendant(node, 1);

        node.isLocked = true;
        node.id = id;
        lockedNode.add(node);

        return true;
    }

    //update anc_locked counter for all descendants recursively
    static void informDescendant(TreeNode node, int val) {
        if (node == null) return;

        node.anc_locked += val;
        for (TreeNode child : node.child) {
            informDescendant(child, val);
        }
    }

    //unlock the node if it is locked by the given user ID
    static boolean unlock(TreeNode node, int id) {
        if (!node.isLocked || node.id != id) {
            return false;
        }

        //decrement des_locked count in all ancestors
        TreeNode cur = node.parent;
        while (cur != null) {
            waitAndRun(cur);
            cur.des_locked--;
            runNext(cur);
            cur = cur.parent;
        }

        //decrement anc_locked for all descendants
        informDescendant(node, -1);

        node.isLocked = false;
        node.id = 0;
        lockedNode.remove(node);

        return true;
    }

    //upgrade operation: unlock all locked descendants (same user) and lock the current node
        static boolean upgrade(TreeNode node, int id) {
        //conditions for upgrade: not already locked, no locked ancestors, at least one locked descendant
               if (node.isLocked || node.anc_locked > 0 || node.des_locked == 0) {
                 return false;
         }

        ArrayList<TreeNode> toUnlock = new ArrayList<>();

        //find all locked descendants locked by the same user
        for (TreeNode locked : lockedNode) {
            if (isDescendant(node, locked)) {
                if (locked.id != id) return false; //if locked by a different user, fail
                toUnlock.add(locked);
            }
        }

        //unlock all such descendants
        for (TreeNode locked : toUnlock) {
            waitAndRun(locked);
            unlockFast(locked); //no id check required
            runNext(locked);
        }

        return lock(node, id); //now lock the current node
    }

    //unlock the node without checking ownership (used during upgrade)
    static void unlockFast(TreeNode node) {
          TreeNode cur = node.parent;
              while (cur != null) {
                  waitAndRun(cur);
                  cur.des_locked--;
                  runNext(cur);
                  cur = cur.parent;
        }

        informDescendant(node, -1);
        node.isLocked = false;
        node.id = 0;
        lockedNode.remove(node);
    }

    //check whether node is a descendant of ancestor
    static boolean isDescendant(TreeNode ancestor, TreeNode node) {
        TreeNode cur = node;
        while (cur != null) {
            if (cur == ancestor) return true;
            cur = cur.parent;
        }
        return false;
    }

    //wait until the current node is not in use, then take control
    static void waitAndRun(TreeNode node) {
        if (node.inUse) {
            //a sample task to represent a waiting task
            Runnable waitTask = () -> {};
            node.queue.add(waitTask);

            //busy wait until the sample task is at the front
            while (node.queue.peek() != waitTask) {
                //hold
            }

            //remove the sample and take control
            node.queue.remove(waitTask);
        }
        node.inUse = true;
    }
}
