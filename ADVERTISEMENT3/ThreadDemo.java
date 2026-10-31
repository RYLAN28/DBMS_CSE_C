// Thread by extending Thread class: shows one ad on one platform
class AdDisplayThread extends Thread {
    private String adName;
    private int times;

    AdDisplayThread(String adName, int times) {
        this.adName = adName;
        this.times = times;
    }

    @Override
    public void run() {
        for (int i = 1; i <= times; i++) {
            System.out.println(adName + " shown " + i + " time(s)");
            try {
                Thread.sleep(500);              // wait 0.5 second
            } catch (InterruptedException e) {
                System.out.println(adName + " was interrupted.");
            }
        }
        System.out.println(adName + " finished.");
    }
}

// Thread by implementing Runnable: payment reminder
class PaymentReminder implements Runnable {
    @Override
    public void run() {
        for (int i = 1; i <= 3; i++) {
            System.out.println("[Reminder " + i + "] Please pay pending advertisement payments.");
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                System.out.println("Reminder interrupted.");
            }
        }
    }
}

// Shared counter: synchronized stops threads from mixing up the number
class ViewCounter {
    private int views = 0;

    public synchronized void addView() {
        views++;
    }

    public int getViews() {
        return views;
    }
}

// Background (daemon) thread: checks for expired ads again and again
class ExpiryChecker implements Runnable {
    @Override
    public void run() {
        while (true) {
            System.out.println("   [Daemon] Checking for expired ads...");
            // later you can call DBFeatures.expireOldAds(); here
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}

public class ThreadDemo {
    public static void main(String[] args) throws InterruptedException {

        // PART 1: multiple threads running together
        System.out.println("=== PART 1: Multiple threads ===");
        AdDisplayThread t1 = new AdDisplayThread("Instagram Ad", 3);
        AdDisplayThread t2 = new AdDisplayThread("YouTube Ad", 3);
        AdDisplayThread t3 = new AdDisplayThread("TV Ad", 3);

        t1.start();
        t2.start();
        t3.start();

        t1.join();      // main waits until the thread is finished
        t2.join();
        t3.join();
        System.out.println("All ads finished.\n");

        // PART 2: Runnable thread
        System.out.println("=== PART 2: Runnable thread ===");
        Thread reminder = new Thread(new PaymentReminder());
        reminder.start();
        reminder.join();
        System.out.println();

        // PART 3: multithreading with shared data (synchronized)
        System.out.println("=== PART 3: Shared counter ===");
        ViewCounter counter = new ViewCounter();

        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                counter.addView();
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        Thread c = new Thread(task);
        a.start(); b.start(); c.start();
        a.join();  b.join();  c.join();

        System.out.println("Total views (should be 3000): " + counter.getViews() + "\n");

        // PART 4: daemon thread
        System.out.println("=== PART 4: Daemon thread ===");
        Thread checker = new Thread(new ExpiryChecker());
        checker.setDaemon(true);        // dies automatically when main ends
        checker.start();

        Thread.sleep(3500);             // main works for 3.5 seconds
        System.out.println("Main is ending, so the daemon thread stops too.");
    }
}
