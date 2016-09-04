import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.*;
import com.esotericsoftware.minlog.Log;
import model.*;
import model.request.*;
import model.response.*;

/**
 * Created by heat_wave on 6/19/16.
 */
public class NodeImpl implements DKVSNode, Runnable{

    private int currentTerm = 0;
    private int commitIndex = 0;
    private int lastApplied = 0;
    private ArrayList<Command> log;

    private NodeState nodeState = NodeState.FOLLOWER;

    private int [] nextIndex = new int[Constants.SERVER_COUNT + 1];
    private int [] matchIndex = new int[Constants.SERVER_COUNT + 1];

    private Client[] client;
    private Server self;
    private Timer timer;

    private StateMachine stateMachine;

    private int nodeId;
    private Properties properties;
    Integer votedFor = null;
    int votesReceived = 0;

    public NodeImpl(int id) throws IOException {
        this.nodeId = id;
        String filename = String.format("dkvs_%d.log", this.nodeId);
        this.stateMachine = new StateMachine(new File(filename));

        properties = new Properties();
        String propFileName = "dkvs.properties";
        InputStream inputStream = new FileInputStream(propFileName);
        properties.load(inputStream);

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final Runnable timeoutChecker = new Runnable() {
            public void run() {
                //no interaction from leader received
                if (nodeState != NodeState.LEADER) {
                    //startElection();
                }
            }
        };
        scheduler.scheduleWithFixedDelay(timeoutChecker, 200, 200, TimeUnit.MILLISECONDS);

        self = new Server();
        Kryo kryo = self.getKryo();
        kryo.register(ArrayList.class);
        kryo.register(String.class);
        kryo.register(Entry.class);
        kryo.register(ElectionVoteRequest.class);
        kryo.register(ElectionVoteResponse.class);
        kryo.register(AppendEntriesRequest.class);
        kryo.register(AppendEntriesResponse.class);
        self.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                super.received(connection, object);

                if (object instanceof Request) {
                    Log.info("Messaging", String.format("Node %d received request: %s", nodeId, object.toString()));
                }

                if (object instanceof AppendEntriesRequest) {
                    if (((AppendEntriesRequest) object).getTerm() < currentTerm) {
                        connection.sendTCP(new AppendEntriesResponse(currentTerm, false));
                        return;
                    }
                    if (nodeState == NodeState.CANDIDATE) {
                        nodeState = NodeState.FOLLOWER;
                    }
                    scheduler.scheduleWithFixedDelay(timeoutChecker, 200, 200, TimeUnit.MILLISECONDS);
                    AppendEntriesRequest request = (AppendEntriesRequest) object;
                    if (request.getEntries().isEmpty()) {
                        connection.sendTCP(new AppendEntriesResponse(currentTerm, true));
                        return;
                    }
                    if (!stateMachine.checkEntryValidity(request.getPrevLogIndex(),request.getPrevLogTerm())) {
                        connection.sendTCP(new AppendEntriesResponse(currentTerm, false));
                        return;
                    }
                    stateMachine.appendEntries(request.getPrevLogIndex(), request.getEntries());
                    if (request.getLeaderCommit() > commitIndex) {
                        commitIndex = Math.min(request.getLeaderCommit(), stateMachine.getLastLogEntry().getIndex());
                    }
                    connection.sendTCP(new AppendEntriesResponse(currentTerm, true));
                }
                if (object instanceof ElectionVoteRequest) {
                    if (((ElectionVoteRequest) object).getTerm() < currentTerm) {
                        connection.sendTCP(new ElectionVoteResponse(currentTerm, false));
                        return;
                    }
                    currentTerm = ((ElectionVoteRequest) object).getTerm();
                    if (votedFor == null || votedFor == ((ElectionVoteRequest) object).getTerm()) {
                        ElectionVoteRequest request = (ElectionVoteRequest) object;
                        Entry lastEntry = stateMachine.getLastLogEntry();
                        if (lastEntry == null || request.getLastLogIndex() >= lastEntry.getIndex() &&
                                request.getLastLogTerm() >= lastEntry.getTerm()) {
                            votedFor = ((ElectionVoteRequest) object).getCandidateId();
                            connection.sendTCP(new ElectionVoteResponse(currentTerm, true));
                            return;
                        }
                    }
                    connection.sendTCP(new ElectionVoteResponse(currentTerm, false));
                }
            }

            @Override
            public void connected(Connection connection) {
                super.connected(connection);
                //connection.sendTCP("abc " + nodeId);
            }
        });
        self.start();

        String address = properties.getProperty("node." + nodeId);
        String[] addressParts = address.split(":");
        try {
            self.bind(Integer.parseInt(addressParts[1]));
        } catch (IOException e) {
            e.printStackTrace();
        }

        client = new Client[Constants.SERVER_COUNT + 1];
        for (int i = 1; i <= 5; i++) {
            if (i == nodeId) {
                continue;
            }
            client[i] = new Client();
            client[i].start();
            kryo = client[i].getKryo();
            kryo.register(ArrayList.class);
            kryo.register(String.class);
            kryo.register(Entry.class);
            kryo.register(ElectionVoteRequest.class);
            kryo.register(ElectionVoteResponse.class);
            kryo.register(AppendEntriesRequest.class);
            kryo.register(AppendEntriesResponse.class);
            int finalI = i;
            client[i].addListener(new Listener() {
                @Override
                public void received(Connection connection, Object object) {
                    super.received(connection, object);
                    if (object instanceof Response) {
                        Log.info("Messaging", String.format("Node %d received response: %s", nodeId, object.toString()));
                    }
                    if (object instanceof ElectionVoteResponse) {
                        if (((ElectionVoteResponse) object).getTerm() == currentTerm &&
                                ((ElectionVoteResponse) object).isVoteGranted()) {
                            votesReceived++;
                        }
                    }
                    if (object instanceof AppendEntriesResponse) {
                        currentTerm = Math.max(currentTerm, ((AppendEntriesResponse) object).getTerm());
                        if (!((AppendEntriesResponse) object).isSuccess()) {
                            nextIndex[finalI]--;
                            client[finalI].sendTCP(new AppendEntriesRequest(
                                    currentTerm,
                                    nodeId,
                                    nextIndex[finalI] - 1,
                                    stateMachine.getEntry(nextIndex[finalI] - 1).getTerm(),
                                    commitIndex,
                                    stateMachine.getEntriesStartingWith(nextIndex[finalI] - 1)));
                        } else {
                            //so what?
                        }
                    }
                }
            });
        }
    }

    @Override
    public ElectionVoteResponse requestVote(int term, int candidateId, int lastLogIndex, int lastLogerm) {
        return null;
    }

    @Override
    public AppendEntriesResponse appendEntries(int term, int leaderId, int prevLogIndex, List<String> entries, int leaderCommitIndex) {
        return null;
    }

    @Override
    public void startElection() {
        Log.info("Election", String.format("Node %d has started election", nodeId));

        this.nodeState = NodeState.CANDIDATE;
        this.currentTerm++;
        this.votedFor = this.nodeId;
        int electionTimeout = new Random().nextInt(150) + 150;
        votesReceived = 1;

        final ExecutorService executor = Executors.newFixedThreadPool(Constants.SERVER_COUNT - 1);
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        final Runnable voteChecker = new Runnable() {
            public void run() {
                if (votesReceived > Constants.SERVER_COUNT / 2) {
                    nodeState = NodeState.LEADER;
                    Log.info("Election", String.format("Node %d became leader with %d votes in term %d",
                            nodeId, votesReceived, currentTerm));
                    for (int i = 1; i <= Constants.SERVER_COUNT; i++) {
                        if (i == nodeId) {
                            continue;
                        }
                        nextIndex[i] = stateMachine.getLogSize() + 1;
                    }
                    sendRequests();
                    scheduler.shutdown();
                }
            }
        };
        scheduler.scheduleWithFixedDelay(voteChecker, electionTimeout, electionTimeout, TimeUnit.MILLISECONDS);

        Collection<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 1; i <= Constants.SERVER_COUNT; i++) {
            if (i == nodeId) {
                continue;
            }
            Entry entry = stateMachine.getLastLogEntry();
            tasks.add(new SendPackageTask(client[i], new ElectionVoteRequest(currentTerm, nodeId,
                    entry != null ? entry.getIndex() : 0,
                    entry != null ? entry.getTerm() : 0)));
        }
        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Log.error("Interruption: ", e.getMessage());
        }

        executor.shutdown(); //always reclaim resources
    }

    @Override
    public void connectToSiblings() {
        for (int i = 1; i <= Constants.SERVER_COUNT; i++) {
            if (i == nodeId) {
                continue;
            }
            try {
                String address = properties.getProperty("node." + i);
                String[] addressParts = address.split(":");
                client[i].connect(5000, addressParts[0], Integer.parseInt(addressParts[1]));
            } catch (IOException e) {
                Log.error("Exception caught: ", e.getMessage());
            }
        }
    }

    private void sendRequests() {
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final ExecutorService executor = Executors.newFixedThreadPool(Constants.SERVER_COUNT - 1);
        scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    Collection<Callable<Response>> tasks = new ArrayList<>();
                    for (int i = 1; i <= Constants.SERVER_COUNT; i++) {
                        if (i == nodeId) {
                            continue;
                        }
                        tasks.add(new SendPackageTask(client[i], new AppendEntriesRequest(
                                currentTerm,
                                nodeId,
                                nextIndex[i] - 1,
                                stateMachine.getEntry(nextIndex[i] - 1) == null ? currentTerm : stateMachine.getEntry(nextIndex[i] - 1).getTerm(),
                                commitIndex,
                                stateMachine.getEntriesStartingWith(nextIndex[i] - 1))));
                    }
                    try {
                        executor.invokeAll(tasks);
                        if (nodeState != NodeState.LEADER) {
                            executor.shutdownNow();
                            scheduler.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        connectToSiblings();
        if (nodeId == 1)
            startElection(); //TODO: find out if nondeterministic election works
    }

    public void stop() {
        self.stop();
    }

    private enum NodeState {
        FOLLOWER,
        LEADER,
        CANDIDATE
    }
}
