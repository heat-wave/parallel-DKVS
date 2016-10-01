import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.*;
import com.esotericsoftware.minlog.Log;
import com.sun.istack.internal.Nullable;
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

    private NodeState nodeState = NodeState.FOLLOWER;

    private int [] nextIndex = new int[Constants.SERVER_COUNT + 1];
    private int [] matchIndex = new int[Constants.SERVER_COUNT + 1];

    private Client[] client;
    private Server self;
    private int timeout;

    private int leaderId;

    private StateMachine stateMachine;

    private int nodeId;
    private Properties properties;
    private Integer votedFor = null;
    private int votesReceived = 0;
    private ScheduledFuture electionFuture;

    public NodeImpl(int id) throws IOException {
        this.nodeId = id;
        String filename = String.format("dkvs_%d.log", this.nodeId);
        this.stateMachine = new StateMachine(new File(filename));
        if (stateMachine.getLogSize() > 0) {
            this.currentTerm = stateMachine.getLastLogEntry().getTerm();
        }

        properties = new Properties();
        String propFileName = "dkvs.properties";
        InputStream inputStream = new FileInputStream(propFileName);
        properties.load(inputStream);
        //timeout = new Random().nextInt(150) + 1500;
        timeout = Integer.parseInt(properties.getProperty("timeout"));

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final Runnable timeoutChecker = () -> {
            //no interaction from leader received
            if (nodeState != NodeState.LEADER) {
                startElection();
            }
        };
        int electionTimeout = timeout + 100 + new Random().nextInt(200);
        electionFuture = scheduler.scheduleWithFixedDelay(timeoutChecker, electionTimeout, electionTimeout, TimeUnit.MILLISECONDS);

        self = new Server();
        Utils.registerClasses(self.getKryo());

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
                    currentTerm = Math.max(currentTerm, ((AppendEntriesRequest) object).getTerm());
                    if (nodeState == NodeState.CANDIDATE) {
                        nodeState = NodeState.FOLLOWER;
                        votedFor = null;
                        votesReceived = 0;
                    }
                    electionFuture.cancel(true);
                    electionFuture = scheduler.scheduleWithFixedDelay(timeoutChecker, electionTimeout, electionTimeout, TimeUnit.MILLISECONDS);
                    AppendEntriesRequest request = (AppendEntriesRequest) object;
                    leaderId = request.getLeaderId();
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
                    if (commitIndex >= lastApplied) {
                        stateMachine.apply(lastApplied);
                        Log.debug("Log", String.format("Applied entry #%d on server %d", lastApplied, nodeId));
                        lastApplied++;
                    }
                    connection.sendTCP(new AppendEntriesResponse(currentTerm, true));
                }
                if (object instanceof ElectionVoteRequest) {
                    if (((ElectionVoteRequest) object).getTerm() < currentTerm) {
                        Log.debug("Election", String.format("Server %d did not grant vote to node %d: its term is smaller", nodeId, ((ElectionVoteRequest) object).getCandidateId()));
                        connection.sendTCP(new ElectionVoteResponse(currentTerm, false));
                        return;
                    }
                    currentTerm = ((ElectionVoteRequest) object).getTerm();
                    if (votedFor == null || votedFor == ((ElectionVoteRequest) object).getCandidateId()) {
                        ElectionVoteRequest request = (ElectionVoteRequest) object;
                        Entry lastEntry = stateMachine.getLastLogEntry();
                        if (lastEntry == null || request.getLastLogIndex() >= lastEntry.getIndex() &&
                                request.getLastLogTerm() >= lastEntry.getTerm()) {
                            votedFor = ((ElectionVoteRequest) object).getCandidateId();
                            Log.debug("Election", String.format("Server %d granted vote to node %d", nodeId, ((ElectionVoteRequest) object).getCandidateId()));
                            connection.sendTCP(new ElectionVoteResponse(currentTerm, true));
                            return;
                        }
                    }
                    Log.debug("Election", String.format("Server %d did not grant vote to node %d: its vote belongs to %d", nodeId, ((ElectionVoteRequest) object).getCandidateId(), votedFor));
                    connection.sendTCP(new ElectionVoteResponse(currentTerm, false));
                }
                if (object instanceof ClientGetRequest) {
                    ClientGetRequest request = (ClientGetRequest) object;
                    connection.sendTCP(new ClientGetResponse(stateMachine.get(request.getKey())));
                }
                if (object instanceof ClientAddRequest) {
                    if (nodeState != NodeState.LEADER) {
                        client[leaderId].sendTCP(object);
                    } else {
                        ClientAddRequest request = (ClientAddRequest) object;
                        stateMachine.addEntryFromClient(new Entry(Entry.Type.SET, request.getKey(),
                                request.getValue(), currentTerm, stateMachine.getLogSize()));
                    }
                }
                if (object instanceof ClientRemoveRequest) {
                    if (nodeState != NodeState.LEADER) {
                        client[leaderId].sendTCP(object);
                    } else {
                        ClientRemoveRequest request = (ClientRemoveRequest) object;
                        stateMachine.addEntryFromClient(new Entry(Entry.Type.DELETE, request.getKey(),
                                null, currentTerm, stateMachine.getLogSize()));
                    }
                }
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
            Utils.registerClasses(client[i].getKryo());

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
                            matchIndex[finalI] = nextIndex[finalI];
                            nextIndex[finalI] = Math.min(nextIndex[finalI] + 1, stateMachine.getLogSize() + 1);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void startElection() {
        Log.info("Election", String.format("Node %d has started election", nodeId));

        this.nodeState = NodeState.CANDIDATE;
        this.currentTerm++;
        this.votedFor = this.nodeId;
        int electionTimeout = timeout; //new Random().nextInt(150) + 150;
        votesReceived = 1;

        final ExecutorService executor = Executors.newFixedThreadPool(Constants.SERVER_COUNT - 1);
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        final Runnable voteChecker = () -> {
            if (votesReceived > Constants.SERVER_COUNT / 2) {
                nodeState = NodeState.LEADER;
                Log.info("Election", String.format("Node %d became leader with %d votes in term %d",
                        nodeId, votesReceived, currentTerm));
                commitIndex = 0;
                for (int i = 1; i <= Constants.SERVER_COUNT; i++) {
                    if (i == nodeId) {
                        continue;
                    }
                    nextIndex[i] = stateMachine.getLogSize() + 1;
                    matchIndex[i] = 0;
                }
                sendRequests();
                scheduler.shutdown();
            }
        };

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
        scheduler.scheduleWithFixedDelay(voteChecker, electionTimeout, electionTimeout, TimeUnit.MILLISECONDS);

        //executor.shutdown(); //always reclaim resources
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
        scheduler.scheduleAtFixedRate(() -> {
            Collection<Callable<Response>> tasks = new ArrayList<>();
            for (int i = 1; i <= Constants.SERVER_COUNT; i++) {
                if (i == nodeId) {
                    continue;
                }
                tasks.add(new SendPackageTask(client[i], new AppendEntriesRequest(
                        currentTerm,
                        nodeId,
                        nextIndex[i] - 1,
                        stateMachine.getEntry(nextIndex[i] - 1) == null ? currentTerm :
                                stateMachine.getEntry(nextIndex[i] - 1).getTerm(),
                        commitIndex,
                        stateMachine.getEntriesStartingWith(nextIndex[i] - 1))));
            }
            try {
                executor.invokeAll(tasks);
                Log.info("Match indices", Arrays.toString(matchIndex));
                int appliedCount = 0;
                matchIndex[nodeId] = stateMachine.getLogSize() + 1;
                for (int i = 1; i <= Constants.SERVER_COUNT; i++) { //get only majority!
                    if (matchIndex[i] > lastApplied) {
                        appliedCount++;
                    }
                }
                if (appliedCount * 2 > Constants.SERVER_COUNT) {
                    stateMachine.apply(lastApplied);
                    commitIndex = lastApplied;
                    lastApplied++;
                }
                if (nodeState != NodeState.LEADER) {
                    executor.shutdownNow();
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.error("Interruption", e.getMessage());
            }
        }, 0, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        connectToSiblings();
    }

    void stop() {
        self.stop();
    }

    private enum NodeState {
        FOLLOWER,
        LEADER,
        CANDIDATE
    }
}
