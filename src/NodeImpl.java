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

    private int [] nextIndex;
    private int [] matchIndex;

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

        self = new Server();
        Kryo kryo = self.getKryo();
        kryo.register(ElectionVoteRequest.class);
        kryo.register(ElectionVoteResponse.class);
        kryo.register(AppendEntriesRequest.class);
        kryo.register(AppendEntriesResponse.class);
        kryo.register(String.class);
        self.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                super.received(connection, object);

                if (object instanceof Request) {
                    Logger.getGlobal().log(Level.INFO, object.getClass().getName());
                }

                if (object instanceof AppendEntriesRequest) {
                    if (((AppendEntriesRequest) object).getTerm() < currentTerm) {
                        connection.sendTCP(new AppendEntriesResponse(currentTerm, false));
                        return;
                    }
                    if (nodeState == NodeState.CANDIDATE) {
                        nodeState = NodeState.FOLLOWER;
                    }
                    AppendEntriesRequest request = (AppendEntriesRequest) object;
                    if (!stateMachine.checkEntryValidity(request.getPrevLogIndex(),request.getPrevLogTerm())) {
                        connection.sendTCP(new AppendEntriesResponse(currentTerm, false));
                        return;
                    }
                    stateMachine.appendEntries(request.getEntries());
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
            kryo.register(ElectionVoteRequest.class);
            kryo.register(ElectionVoteResponse.class);
            kryo.register(AppendEntriesRequest.class);
            kryo.register(AppendEntriesResponse.class);
            kryo.register(String.class);
            client[i].addListener(new Listener() {
                @Override
                public void received(Connection connection, Object object) {
                    super.received(connection, object);
                    if (object instanceof Response) {
                        Logger.getGlobal().log(Level.INFO, object.toString());
                    }
                    if (object instanceof ElectionVoteResponse) {
                        if (((ElectionVoteResponse) object).getTerm() == currentTerm &&
                                ((ElectionVoteResponse) object).isVoteGranted()) {
                            votesReceived++;
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
                    Logger.getGlobal().log(Level.INFO,
                            "Node " + nodeId + " became leader with " + votesReceived + " votes in term " + currentTerm);
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
            Logger.getGlobal().log(Level.SEVERE, e.getMessage());
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
                //e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        connectToSiblings();
        if (nodeId == 1)
            startElection();
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
