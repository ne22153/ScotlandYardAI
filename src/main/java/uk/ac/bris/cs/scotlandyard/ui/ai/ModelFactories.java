package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Supplier;

class ModelFactories {

    /**
     * @return factories that will be used throughout the parameterised tests.
     */
    public static ImmutableList<
            Map.Entry<
                    Supplier<ScotlandYard.Factory<Board.GameState>>,
                    Supplier<ScotlandYard.Factory<Model>>
                    >
            > factories() {
        return ImmutableList.of(
                new AbstractMap.SimpleImmutableEntry<>(MyGameStateFactory::new, MyModelFactory::new));
    }


}
