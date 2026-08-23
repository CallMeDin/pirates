package ace.actually.pirates.entities.friendly_pirate;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.IllagerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.IllagerEntityModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class FriendlyPirateRenderer extends IllagerEntityRenderer<FriendlyPirateEntity> {
    private static final Identifier VILLAGER =
            new Identifier("minecraft", "textures/entity/villager/villager.png");
    private static final Identifier PIRATE_LAYER =
            new Identifier("pirates", "textures/entity/pirate1.png");

    public FriendlyPirateRenderer(EntityRendererFactory.Context context) {
        super(context, new IllagerEntityModel<>(context.getPart(EntityModelLayers.PILLAGER)), 0.5F);
        this.addFeature(new PirateClothingFeature(this));
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(FriendlyPirateEntity entity) {
        return VILLAGER;
    }

    private static final class PirateClothingFeature
            extends FeatureRenderer<FriendlyPirateEntity, IllagerEntityModel<FriendlyPirateEntity>> {
        private PirateClothingFeature(
                FeatureRendererContext<FriendlyPirateEntity, IllagerEntityModel<FriendlyPirateEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light,
                           FriendlyPirateEntity entity, float limbAngle, float limbDistance,
                           float tickDelta, float animationProgress, float headYaw, float headPitch) {
            VertexConsumer consumer = vertices.getBuffer(RenderLayer.getEntityTranslucent(PIRATE_LAYER));
            getContextModel().render(matrices, consumer, light, OverlayTexture.DEFAULT_UV,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}