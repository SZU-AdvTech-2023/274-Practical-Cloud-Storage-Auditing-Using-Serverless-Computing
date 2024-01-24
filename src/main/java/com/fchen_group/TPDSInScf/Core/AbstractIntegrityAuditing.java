package com.fchen_group.TPDSInScf.Core;

import java.io.IOException;

public interface  AbstractIntegrityAuditing {
     int DATA_SHARDS = 0;
    int PARITY_SHARDS = 0;
    int SHARD_NUMBER = 0;
    String Key = null;
    String sKey = null;
    public byte[][] parity = new byte[0][]; // the final calculated parity


    public abstract void genKey();

    public abstract long outSource() throws IOException;

    public abstract ChallengeData audit(int challengeLen);

    public abstract ProofData prove(ChallengeData challengeData, byte[][] downloadData, byte[][] downloadParity);

    public abstract boolean verify(ChallengeData challengeData, ProofData proofData);

}
