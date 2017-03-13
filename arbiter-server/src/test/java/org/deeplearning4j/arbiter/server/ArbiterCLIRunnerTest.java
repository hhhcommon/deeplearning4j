package org.deeplearning4j.arbiter.server;

import org.apache.commons.io.FileUtils;
import org.deeplearning4j.arbiter.DL4JConfiguration;
import org.deeplearning4j.arbiter.MultiLayerSpace;
import org.deeplearning4j.arbiter.layers.DenseLayerSpace;
import org.deeplearning4j.arbiter.layers.OutputLayerSpace;
import org.deeplearning4j.arbiter.optimize.api.CandidateGenerator;
import org.deeplearning4j.arbiter.optimize.api.data.DataProvider;
import org.deeplearning4j.arbiter.optimize.api.data.DataSetIteratorFactoryProvider;
import org.deeplearning4j.arbiter.optimize.api.termination.MaxCandidatesCondition;
import org.deeplearning4j.arbiter.optimize.api.termination.MaxTimeCondition;
import org.deeplearning4j.arbiter.optimize.candidategenerator.RandomSearchGenerator;
import org.deeplearning4j.arbiter.optimize.config.OptimizationConfiguration;
import org.deeplearning4j.arbiter.optimize.parameter.continuous.ContinuousParameterSpace;
import org.deeplearning4j.arbiter.optimize.parameter.discrete.DiscreteParameterSpace;
import org.deeplearning4j.arbiter.optimize.parameter.integer.IntegerParameterSpace;
import org.deeplearning4j.arbiter.saver.local.multilayer.LocalMultiLayerNetworkSaver;
import org.deeplearning4j.arbiter.scoring.multilayer.TestSetLossScoreFunction;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.Test;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by agibsonccc on 3/12/17.
 */
public class ArbiterCLIRunnerTest {


    @Test
    public void testCliRunner() throws Exception {
        ArbiterCliRunner cliRunner = new ArbiterCliRunner();

        //Define: network config (hyperparameter space)
        MultiLayerSpace mls = new MultiLayerSpace.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(new ContinuousParameterSpace(0.0001, 0.1))
                .regularization(true)
                .l2(new ContinuousParameterSpace(0.0001, 0.01))
                .iterations(100)
                .addLayer(new DenseLayerSpace.Builder().nIn(4).nOut(new IntegerParameterSpace(2,10))
                        .activation(new DiscreteParameterSpace<>("relu","tanh"))
                        .build(),new IntegerParameterSpace(1,2),true)   //1-2 identical layers (except nIn)
                .addLayer(new OutputLayerSpace.Builder().nOut(3).activation("softmax")
                        .lossFunction(LossFunctions.LossFunction.MCXENT).build())
                .numEpochs(3)
                .pretrain(false).backprop(true).build();

        //Define configuration:

        CandidateGenerator<DL4JConfiguration> candidateGenerator = new RandomSearchGenerator<>(mls);
        DataProvider<Object> dataProvider = new DataSetIteratorFactoryProvider();


//        String modelSavePath = FilenameUtils.concat(System.getProperty("java.io.tmpdir"),"ArbiterDL4JTest/");
        String modelSavePath = new File(System.getProperty("java.io.tmpdir"),"ArbiterDL4JTest/").getAbsolutePath();
        File dir = new File(modelSavePath);
        if(!dir.exists())
            dir.mkdirs();
        String configPath = System.getProperty("java.io.tmpdir") + File.separator + UUID.randomUUID().toString() + ".json";
        OptimizationConfiguration<DL4JConfiguration,MultiLayerNetwork,Object,Evaluation> configuration
                = new OptimizationConfiguration.Builder<DL4JConfiguration,MultiLayerNetwork,Object,Evaluation>()
                .candidateGenerator(candidateGenerator)
                .dataProvider(dataProvider)
                .modelSaver(new LocalMultiLayerNetworkSaver<Evaluation>(modelSavePath))
                .scoreFunction(new TestSetLossScoreFunction())
                .terminationConditions(new MaxTimeCondition(2, TimeUnit.MINUTES),
                        new MaxCandidatesCondition(100))
                .build();

        FileUtils.writeStringToFile(new File(configPath),configuration.toJson());

        cliRunner.runMain(
                "--dataSetIteratorClass",
                MnistDataSetIteratorFactory.class.getCanonicalName(),
                "--neuralNetType",
                ArbiterCliRunner.MULTI_LAYER_NETWORK,
                "--optimizationConfigPath",
                configPath
        );
    }

}
