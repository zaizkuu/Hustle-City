package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.render.PersonPainter
import com.hustlecity.game.render.Pose
import com.hustlecity.game.render.Projector
import com.hustlecity.game.render.RenderBatch
import com.hustlecity.game.render.Scene3D
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/** Close-up of the 3D people: walking, umbrella, sitting, dancing. */
class PeopleRenderTest {
    @Test
    fun drawPeople() {
        val bmp = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF9AA3AD.toInt())
        val proj = Projector()
        proj.tx = 0f; proj.ty = 0f; proj.dist = 7f; proj.pitch = Math.toRadians(40.0).toFloat(); proj.yaw = .5f; proj.near = .3f
        proj.update(900f, 500f, .6f)
        val batch = RenderBatch()
        val scene = Scene3D(proj, batch)
        val p = PersonPainter(scene)
        batch.begin(c)
        p.draw(-2.4f, 0f, 0f, 0f, 1f, 1f, 0xFF2F80ED.toInt(), PersonPainter.PANTS[1], 0xFFE0AC69.toInt(), PersonPainter.HAIR[0])
        p.draw(-.8f, 0f, 0f, 1.6f, 2f, 1f, 0xFFEB5757.toInt(), PersonPainter.PANTS[2], 0xFF8D5524.toInt(), PersonPainter.HAIR[3], umbrella = PersonPainter.UMBRELLAS[3])
        p.draw(.8f, 0f, .45f, 3.1f, 0f, 0f, 0xFF27AE60.toInt(), PersonPainter.PANTS[0], 0xFFF1C27D.toInt(), PersonPainter.HAIR[4], pose = Pose.SIT)
        p.draw(2.4f, 0f, 0f, -1f, 2.5f, 0f, 0xFF9B51E0.toInt(), PersonPainter.PANTS[3], 0xFFFFDBAC.toInt(), PersonPainter.HAIR[2], pose = Pose.DANCE, apron = 0xFF2C3E50.toInt())
        batch.flush()
        File("build/snapshots").mkdirs()
        File("build/snapshots/people_poses.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Carrying: a box in both arms, pushing a loaded hand truck and a loaded store cart. */
    @Test
    fun drawCarrying() {
        val bmp = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF9AA3AD.toInt())
        val proj = Projector()
        proj.tx = 0f; proj.ty = 0f; proj.dist = 7f; proj.pitch = Math.toRadians(40.0).toFloat(); proj.yaw = .5f; proj.near = .3f
        proj.update(900f, 500f, .6f)
        val batch = RenderBatch()
        val scene = Scene3D(proj, batch)
        val p = PersonPainter(scene)
        batch.begin(c)
        p.draw(-2.6f, .6f, 0f, -1.2f, 1f, 1f, 0xFF2F80ED.toInt(), PersonPainter.PANTS[1], 0xFFE0AC69.toInt(), PersonPainter.HAIR[0], carry = 1, load = 1)
        p.draw(-.4f, .3f, 0f, -1.2f, 2f, 1f, 0xFFEB5757.toInt(), PersonPainter.PANTS[2], 0xFF8D5524.toInt(), PersonPainter.HAIR[3], carry = 2, load = 3)
        p.draw(1.8f, .8f, 0f, -1.2f, 0f, 1f, 0xFF27AE60.toInt(), PersonPainter.PANTS[0], 0xFFF1C27D.toInt(), PersonPainter.HAIR[4], carry = 3, load = 6)
        batch.flush()
        File("build/snapshots").mkdirs()
        File("build/snapshots/people_carrying.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
